package me.phoenixra.atumvr.core.session;

import lombok.Getter;
import me.phoenixra.atumvr.api.exceptions.AtumVRException;
import me.phoenixra.atumvr.core.XRProvider;
import me.phoenixra.atumvr.core.enums.XRGraphicsApi;
import me.phoenixra.atumvr.core.session.opengl.XROpenGLBinding;
import me.phoenixra.atumvr.core.session.platform.XRPlatform;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * XR session instance (low-level OpenXR stuff)
 */
public class XRInstance {

    private final XRProvider vrProvider;

    @Getter
    protected XrInstance handle;

    @Getter
    protected final XrEventDataBuffer xrEventBuffer;

    @Getter
    private String runtimeName;
    @Getter
    private long runtimeVersion;
    @Getter
    private String runtimeVersionString;


    @Getter
    private Set<String> availableExtensions = Set.of();
    @Getter
    private Set<String> enabledExtensions = Set.of();

    @Getter
    private XRGraphicsApi graphicsApi;

    @Getter
    private XROpenGLBinding.ContextApi glContextApi;

    private XrDebugUtilsMessengerEXT debugMessenger;

    public XRInstance(@NotNull XRProvider vrProvider){
        this.vrProvider = vrProvider;
        this.xrEventBuffer = XrEventDataBuffer.calloc();
    }

    public void init() {
        try (MemoryStack stack = MemoryStack.stackPush()) {

            // Quest/Android: initialise the OpenXR loader first (no-op on desktop). Ported from coah80/AtumVR d04c1669.
            XRPlatform.get().initializeLoader(stack);

            var extensionsPointer = setupExtensions(
                    vrProvider, stack
            );

            // 1) Fill XrApplicationInfo
            var appInfo = XrApplicationInfo.calloc(stack)
                    .applicationName(stack.UTF8(vrProvider.getAppName()))
                    .applicationVersion(1)
                    .engineName(stack.UTF8("AtumEngine"))
                    .engineVersion(1)
                    .apiVersion(XR10.XR_MAKE_VERSION(1, 0, 40));

            // 2) Create XrInstanceCreateInfo
            var instInfo = XrInstanceCreateInfo.calloc(stack)
                    .type(XR10.XR_TYPE_INSTANCE_CREATE_INFO)
                    .next(XRPlatform.get().getInstanceCreateInfo(stack))
                    .applicationInfo(appInfo)
                    .enabledExtensionNames(extensionsPointer)
                    .enabledApiLayerNames(null);

            // 3) Create the instance and handle errors
            var instancePointer = stack.callocPointer(1);
            int result = XR10.xrCreateInstance(instInfo, instancePointer);
            if (result == XR10.XR_ERROR_RUNTIME_FAILURE) {
                throw new AtumVRException("Failed to create XrInstance: runtime failure (is headset connected?)");
            } else if (result == XR10.XR_ERROR_INSTANCE_LOST) {
                throw new AtumVRException("Failed to create XrInstance: instance lost during creation");
            } else if (result != XR10.XR_SUCCESS) {
                vrProvider.checkXRError(result, "xrCreateInstance", "Failed to create XrInstance");
            }

            this.handle = new XrInstance(instancePointer.get(0), instInfo);

            if(!XRPlatform.isAndroid() && handle.getCapabilities().XR_EXT_debug_utils) {
                setupDebugMessenger(vrProvider, stack);
            }

            var properties = XrInstanceProperties.calloc(stack).type$Default();
            vrProvider.checkXRError(
                    XR10.xrGetInstanceProperties(handle, properties),
                    "xrGetInstanceProperties"
            );
            runtimeName = properties.runtimeNameString();
            runtimeVersion = properties.runtimeVersion();
            runtimeVersionString = XR10.XR_VERSION_MAJOR(runtimeVersion)
                    + "." + XR10.XR_VERSION_MINOR(runtimeVersion)
                    + "." + XR10.XR_VERSION_PATCH(runtimeVersion);
        }
    }

    private PointerBuffer setupExtensions(XRProvider vrProvider, MemoryStack stack){


        // 1) Enumerate available instance extensions
        var extCountBuf = stack.callocInt(1);
        vrProvider.checkXRError(
                XR10.xrEnumerateInstanceExtensionProperties((ByteBuffer)null, extCountBuf, null),
                "xrEnumerateInstanceExtensionProperties", "count"
        );

        int extCount = extCountBuf.get(0);
        var extProperties = XrExtensionProperties
                .calloc(extCount, stack);
        extProperties.forEach(
                prop -> prop.type(XR10.XR_TYPE_EXTENSION_PROPERTIES)
        );

        vrProvider.checkXRError(
                XR10.xrEnumerateInstanceExtensionProperties((ByteBuffer)null, extCountBuf, extProperties),
                "xrEnumerateInstanceExtensionProperties", "properties"
        );

        // Collect supported extension names
        Set<String> available = new HashSet<>(extCount);
        for (XrExtensionProperties prop : extProperties) {
            available.add(prop.extensionNameString());
        }
        availableExtensions = Set.copyOf(available);


        // On Android the context is EGL/GLES and is bound through XR_KHR_opengl_es_enable
        // (XRPlatform.Android), not through one of the desktop OpenGL context APIs.
        glContextApi = XRPlatform.get().usesAndroidBridge() ? null : XROpenGLBinding.detectContextApi();
        graphicsApi = resolveGraphicsApi();
        vrProvider.getLogger().logInfo(
                "Graphics API: " + graphicsApi + ", OpenGL context: " + glContextApi
        );

        // 2) Define desired extensions in priority order
        List<String> desiredExtensions = new ArrayList<>(requiredExtensions(graphicsApi));
        desiredExtensions.addAll(List.of(
                FBDisplayRefreshRate.XR_FB_DISPLAY_REFRESH_RATE_EXTENSION_NAME,
                KHRVisibilityMask.XR_KHR_VISIBILITY_MASK_EXTENSION_NAME,

                // Quest runtime extensions (coah80/AtumVR 1d7dd401); each is only enabled when the runtime offers it
                EXTLocalFloor.XR_EXT_LOCAL_FLOOR_EXTENSION_NAME,
                EXTPerformanceSettings.XR_EXT_PERFORMANCE_SETTINGS_EXTENSION_NAME,
                FBColorSpace.XR_FB_COLOR_SPACE_EXTENSION_NAME,
                FBFoveation.XR_FB_FOVEATION_EXTENSION_NAME,
                FBFoveationConfiguration.XR_FB_FOVEATION_CONFIGURATION_EXTENSION_NAME,
                FBSwapchainUpdateState.XR_FB_SWAPCHAIN_UPDATE_STATE_EXTENSION_NAME
        ));
        // no debug utils messenger on Android (coah80/AtumVR 1d7dd401)
        if (!XRPlatform.isAndroid()) {
            desiredExtensions.add(EXTDebugUtils.XR_EXT_DEBUG_UTILS_EXTENSION_NAME);
        }
        desiredExtensions.addAll(vrProvider.getXRAppExtensions());

        // Keep only what the runtime offers, preserving priority order and dropping duplicates
        Set<String> enabled = new LinkedHashSet<>();
        List<String> skipped = new ArrayList<>();
        for (String extName : desiredExtensions) {
            if (availableExtensions.contains(extName)) {
                enabled.add(extName);
            } else if (!skipped.contains(extName)) {
                skipped.add(extName);
            }
        }
        enabledExtensions = Set.copyOf(enabled);

        if (!skipped.isEmpty()) {
            vrProvider.getLogger().logDebug(
                    "Requested XR extensions unsupported by the runtime, skipping: " + skipped
            );
        }

        var extensionsPointer = stack.mallocPointer(enabled.size());
        for (String extName : enabled) {
            extensionsPointer.put(stack.UTF8(extName));
        }
        extensionsPointer.flip();
        return extensionsPointer;
    }


    private XRGraphicsApi resolveGraphicsApi() {
        if (XRPlatform.get().usesAndroidBridge()) {
            // Quest: the app renders with OpenGL ES; there is no Vulkan bridge on this path.
            List<String> missing = missingExtensions(XRGraphicsApi.OPENGL);
            if (!missing.isEmpty()) {
                throw new AtumVRException("Missing required graphics extension: " + missing);
            }
            return XRGraphicsApi.OPENGL;
        }
        XRGraphicsApi forced = vrProvider.getGraphicsApiPreference();
        if (forced != null) {
            List<String> missing = missingExtensions(forced);
            if (!missing.isEmpty()) {
                throw new AtumVRException(
                        "Forced graphics API " + forced + " requires " + missing
                                + " which the runtime does not support"
                );
            }
            vrProvider.getLogger().logInfo("Graphics API forced to " + forced);
            return forced;
        }
        List<String> openglMissing = missingExtensions(XRGraphicsApi.OPENGL);
        boolean vulkanSupported = missingExtensions(XRGraphicsApi.VULKAN).isEmpty();
        if (openglMissing.isEmpty()) {
            String arcRenderer = detectIntelArcRenderer();
            if (arcRenderer != null) {
                if (vulkanSupported) {
                    vrProvider.getLogger().logInfo(
                            "Intel Arc GPU detected [" + arcRenderer
                                    + "], using the Vulkan bridge"
                    );
                    return XRGraphicsApi.VULKAN;
                }
                vrProvider.getLogger().logInfo(
                        "Intel Arc GPU detected [" + arcRenderer + "] but the runtime lacks "
                                + XRGraphicsApi.VULKAN.getExtensionName() + ", staying on OpenGL"
                );
            }
            return XRGraphicsApi.OPENGL;
        }
        if (vulkanSupported) {
            vrProvider.getLogger().logInfo(
                    "Runtime lacks " + openglMissing + " for the " + glContextApi
                            + " OpenGL context, using the Vulkan presentation bridge"
            );
            return XRGraphicsApi.VULKAN;
        }
        throw new AtumVRException(
                "Runtime supports neither OpenGL " + requiredExtensions(XRGraphicsApi.OPENGL)
                        + " nor Vulkan " + requiredExtensions(XRGraphicsApi.VULKAN)
        );
    }


    private List<String> requiredExtensions(XRGraphicsApi api) {
        if (api == XRGraphicsApi.OPENGL && XRPlatform.get().usesAndroidBridge()) {
            return List.of(XRPlatform.get().getGraphicsExtension());
        }
        String bindingExtension = api == XRGraphicsApi.OPENGL && glContextApi != null
                ? glContextApi.getExtensionName()
                : null;
        return bindingExtension == null
                ? List.of(api.getExtensionName())
                : List.of(api.getExtensionName(), bindingExtension);
    }

    private List<String> missingExtensions(XRGraphicsApi api) {
        return requiredExtensions(api).stream()
                .filter(name -> !availableExtensions.contains(name))
                .toList();
    }

    /**
     * @return the GL renderer string if the current GPU is an Intel Arc, otherwise null
     */
    private String detectIntelArcRenderer() {
        try {
            GL.getCapabilities();
        } catch (IllegalStateException noContext) {
            vrProvider.getLogger().logDebug(
                    "No current GL context while resolving the graphics API, skipping GPU detection"
            );
            return null;
        }
        String renderer = GL11.glGetString(GL11.GL_RENDERER);
        if (renderer == null) {
            return null;
        }
        String lower = renderer.toLowerCase(Locale.ROOT);
        return lower.contains("intel") && lower.contains("arc") ? renderer : null;
    }

    public boolean isExtensionEnabled(@NotNull String extensionName){
        return enabledExtensions.contains(extensionName);
    }


    public boolean isExtensionAvailable(@NotNull String extensionName){
        return availableExtensions.contains(extensionName);
    }

    private void setupDebugMessenger(XRProvider vrProvider, MemoryStack stack) {
        var createInfo = XrDebugUtilsMessengerCreateInfoEXT
                .calloc(stack)
                .type$Default()  // XR_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT :contentReference[oaicite:0]{index=0}
                .messageSeverities(
                        EXTDebugUtils.XR_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT
                                | EXTDebugUtils.XR_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT
                                | EXTDebugUtils.XR_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT
                                | EXTDebugUtils.XR_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT
                )
                // catch every message type:
                .messageTypes(
                        EXTDebugUtils.XR_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT
                                | EXTDebugUtils.XR_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT
                                | EXTDebugUtils.XR_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT
                                | EXTDebugUtils.XR_DEBUG_UTILS_MESSAGE_TYPE_CONFORMANCE_BIT_EXT
                );


        XrDebugUtilsMessengerCallbackEXTI debugCallback = (messageSeverity, messageTypes, pCallbackData, pUserData) -> {

            var data = XrDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);

            vrProvider.getLogger().logDebug(
                    String.format(
                            "[OpenXR][%s] %s%n",
                            data.functionNameString(),
                            data.messageString()
                    )
            );
            return XR10.XR_FALSE; // don't abort the call that triggered this
        };

        createInfo
                .userCallback(debugCallback)
                .userData(0);

        PointerBuffer pMessenger = stack.callocPointer(1);
        int err = EXTDebugUtils.xrCreateDebugUtilsMessengerEXT(
                handle, createInfo, pMessenger
        );
        vrProvider.checkXRError(
                err, "xrCreateDebugUtilsMessengerEXT", ""
        );
        debugMessenger = new XrDebugUtilsMessengerEXT(pMessenger.get(0), handle);
    }


    public void destroy(){
        if (debugMessenger != null) {
            vrProvider.checkXRError(
                    false,
                    EXTDebugUtils.xrDestroyDebugUtilsMessengerEXT(debugMessenger),
                    "xrDestroyDebugUtilsMessengerEXT",
                    ""
            );
        }
        if (handle != null) {
            vrProvider.checkXRError(
                    false,
                    XR10.xrDestroyInstance(handle),
                    "xrDestroyInstance",
                    ""
            );
        }
        xrEventBuffer.close();
    }
}
