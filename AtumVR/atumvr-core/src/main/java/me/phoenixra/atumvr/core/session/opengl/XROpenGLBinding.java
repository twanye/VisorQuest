package me.phoenixra.atumvr.core.session.opengl;

import lombok.Getter;
import me.phoenixra.atumvr.api.exceptions.AtumVRException;
import me.phoenixra.atumvr.core.XRProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWNativeGLX;
import org.lwjgl.glfw.GLFWNativeWGL;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.glfw.GLFWNativeX11;
import org.lwjgl.opengl.GLX;
import org.lwjgl.opengl.GLX12;
import org.lwjgl.opengl.WGL;
import org.lwjgl.openxr.KHROpenGLEnable;
import org.lwjgl.openxr.MNDXEGLEnable;
import org.lwjgl.openxr.XrGraphicsBindingEGLMNDX;
import org.lwjgl.openxr.XrGraphicsBindingOpenGLWin32KHR;
import org.lwjgl.openxr.XrGraphicsBindingOpenGLXlibKHR;
import org.lwjgl.openxr.XrGraphicsRequirementsOpenGLKHR;
import org.lwjgl.openxr.XrInstance;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Platform;
import org.lwjgl.system.Struct;
import org.lwjgl.system.linux.DynamicLinkLoader;
import org.lwjgl.system.linux.X11;
import org.lwjgl.system.windows.User32;

import java.nio.IntBuffer;
import java.util.Objects;

import static org.lwjgl.opengl.GLX13.*;
import static org.lwjgl.system.MemoryStack.stackInts;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Binds the app's OpenGL context to the OpenXR session
 */
public class XROpenGLBinding {

    private final XRProvider vrProvider;

    public XROpenGLBinding(@NotNull XRProvider vrProvider) {
        this.vrProvider = vrProvider;
    }


    public static @Nullable ContextApi detectContextApi() {
        if (Platform.get() == Platform.WINDOWS) {
            return ContextApi.WGL;
        }
        if (Platform.get() == Platform.LINUX) {
            Egl egl = Egl.get();
            return egl != null && egl.currentContext() != NULL ? ContextApi.EGL : ContextApi.GLX;
        }
        return null;
    }

    public Struct<?> createGraphicsBinding(MemoryStack stack,
                                           XrInstance instance,
                                           long systemId,
                                           @Nullable ContextApi contextApi,
                                           long windowHandle) {
        if (contextApi == null) {
            throw new AtumVRException("No OpenGL graphics binding for " + Platform.get());
        }
        var graphicsRequirements = XrGraphicsRequirementsOpenGLKHR.calloc(stack)
                .type(KHROpenGLEnable.XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_KHR);
        vrProvider.checkXRError(
                KHROpenGLEnable.xrGetOpenGLGraphicsRequirementsKHR(instance, systemId, graphicsRequirements),
                "xrGetOpenGLGraphicsRequirementsKHR"
        );
        return switch (contextApi) {
            case WGL -> createWin32Binding(stack, windowHandle);
            case GLX -> createXlibBinding(stack, windowHandle);
            case EGL -> createEglBinding(stack);
        };
    }

    private Struct<?> createWin32Binding(MemoryStack stack, long windowHandle) {
        long deviceContext;
        long glContext;
        if (windowHandle != NULL) {
            deviceContext = User32.GetDC(GLFWNativeWin32.glfwGetWin32Window(windowHandle));
            glContext = GLFWNativeWGL.glfwGetWGLContext(windowHandle);
        } else {
            deviceContext = WGL.wglGetCurrentDC();
            glContext = wglGetCurrentContext();
            if (glContext == NULL) {
                throw new AtumVRException("No OpenGL context");
            }
        }
        return XrGraphicsBindingOpenGLWin32KHR.calloc(stack).set(
                KHROpenGLEnable.XR_TYPE_GRAPHICS_BINDING_OPENGL_WIN32_KHR,
                NULL,
                deviceContext,
                glContext
        );
    }

    private Struct<?> createXlibBinding(MemoryStack stack, long windowHandle) {
        long xDisplay;
        long glXContext;
        long glXWindowHandle;
        int fbXID;
        if (windowHandle != NULL) {
            xDisplay = GLFWNativeX11.glfwGetX11Display();

            glXContext = GLFWNativeGLX.glfwGetGLXContext(windowHandle);
            glXWindowHandle = GLFWNativeGLX.glfwGetGLXWindow(windowHandle);
            if (xDisplay == NULL || glXContext == NULL) {
                throw new AtumVRException("Window don't have a GLX context or a current EGL context");
            }

            fbXID = glXQueryDrawable(xDisplay, glXWindowHandle, GLX_FBCONFIG_ID);
        } else {
            xDisplay = GLX12.glXGetCurrentDisplay();
            glXContext = GLX.glXGetCurrentContext();
            glXWindowHandle = GLX.glXGetCurrentDrawable();
            if (xDisplay == NULL || glXContext == NULL) {
                throw new AtumVRException("No current GLX or EGL context");
            }

            IntBuffer fbConfigId = stack.mallocInt(1);
            glXQueryContext(xDisplay, glXContext, GLX_FBCONFIG_ID, fbConfigId);
            fbXID = fbConfigId.get(0);
        }
        PointerBuffer fbConfigBuf = glXChooseFBConfig(
                xDisplay, X11.XDefaultScreen(xDisplay),
                stackInts(GLX_FBCONFIG_ID, fbXID, 0)
        );
        if (fbConfigBuf == null) {
            throw new AtumVRException("Framebuffer config is null");
        }
        long fbConfig = fbConfigBuf.get();

        return XrGraphicsBindingOpenGLXlibKHR.calloc(stack).set(
                KHROpenGLEnable.XR_TYPE_GRAPHICS_BINDING_OPENGL_XLIB_KHR,
                NULL,
                xDisplay,
                (int) Objects.requireNonNull(glXGetVisualFromFBConfig(xDisplay, fbConfig)).visualid(),
                fbConfig,
                glXWindowHandle,
                glXContext
        );
    }

    private Struct<?> createEglBinding(MemoryStack stack) {
        Egl egl = Objects.requireNonNull(Egl.get());
        long display = egl.currentDisplay();
        long context = egl.currentContext();
        if (display == NULL || context == NULL) {
            throw new AtumVRException("No current EGL context");
        }
        var binding = XrGraphicsBindingEGLMNDX.calloc(stack)
                .type(MNDXEGLEnable.XR_TYPE_GRAPHICS_BINDING_EGL_MNDX)
                .getProcAddress(egl.getProcAddress)
                .display(display)
                .context(context);
        long config = egl.contextConfig(display, context, stack);
        if (config != NULL) {
            binding.config(config);
        }
        return binding;
    }

    private static long wglGetCurrentContext() {
        try {
            try {
                return (long) WGL.class.getMethod("wglGetCurrentContext").invoke(null);
            } catch (NoSuchMethodException e) {
                return (long) WGL.class.getMethod("wglGetCurrentContext", IntBuffer.class)
                        .invoke(null, (Object) null);
            }
        } catch (ReflectiveOperationException e) {
            throw new AtumVRException("Could not query OpenGL context", e);
        }
    }

    @Getter
    public enum ContextApi {
        WGL(null),
        GLX(null),
        EGL(MNDXEGLEnable.XR_MNDX_EGL_ENABLE_EXTENSION_NAME);

        private final @Nullable String extensionName;

        ContextApi(@Nullable String extensionName) {
            this.extensionName = extensionName;
        }
    }

    private static final class Egl {
        private static final int EGL_CONFIG_ID = 0x3028;
        private static final int EGL_NONE = 0x3038;

        private static final @Nullable Egl LOADED = load();

        private final long getProcAddress;
        private final long getCurrentDisplay;
        private final long getCurrentContext;
        private final long queryContext;
        private final long chooseConfig;

        private Egl(long library) {
            getProcAddress = DynamicLinkLoader.dlsym(library, "eglGetProcAddress");
            getCurrentDisplay = DynamicLinkLoader.dlsym(library, "eglGetCurrentDisplay");
            getCurrentContext = DynamicLinkLoader.dlsym(library, "eglGetCurrentContext");
            queryContext = DynamicLinkLoader.dlsym(library, "eglQueryContext");
            chooseConfig = DynamicLinkLoader.dlsym(library, "eglChooseConfig");
        }

        static @Nullable Egl get() {
            return LOADED;
        }

        private static @Nullable Egl load() {
            long library = DynamicLinkLoader.dlopen(
                    "libEGL.so.1",
                    DynamicLinkLoader.RTLD_LAZY | DynamicLinkLoader.RTLD_NOLOAD
            );
            if (library == NULL) {
                return null;
            }
            Egl egl = new Egl(library);
            boolean complete = egl.getProcAddress != NULL && egl.getCurrentDisplay != NULL
                    && egl.getCurrentContext != NULL && egl.queryContext != NULL && egl.chooseConfig != NULL;
            return complete ? egl : null;
        }

        long currentDisplay() {
            return JNI.callP(getCurrentDisplay);
        }

        long currentContext() {
            return JNI.callP(getCurrentContext);
        }


        long contextConfig(long display, long context, MemoryStack stack) {
            IntBuffer configId = stack.callocInt(1);
            if (JNI.callPPPI(display, context, EGL_CONFIG_ID, memAddress(configId), queryContext) == 0) {
                throw new AtumVRException("eglQueryContext(EGL_CONFIG_ID) failed");
            }
            if (configId.get(0) == 0) {
                return NULL;
            }
            IntBuffer attributes = stack.ints(EGL_CONFIG_ID, configId.get(0), EGL_NONE);
            PointerBuffer config = stack.callocPointer(1);
            IntBuffer count = stack.callocInt(1);
            int found = JNI.callPPPPI(display, memAddress(attributes), memAddress(config), 1,
                    memAddress(count), chooseConfig);
            if (found == 0 || count.get(0) == 0) {
                throw new AtumVRException("No EGLConfig with id " + configId.get(0));
            }
            return config.get(0);
        }
    }
}
