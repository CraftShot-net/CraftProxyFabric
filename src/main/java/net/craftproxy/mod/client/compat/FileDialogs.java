package net.craftproxy.mod.client.compat;

//? if >=26.3 {
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;

import java.nio.ByteBuffer;
//?} else {
/*import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
*///?}

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Native "open file" dialog. Minecraft ships TinyFileDialogs up to 26.2; 26.3 replaced GLFW
 * with SDL3, which has its own asynchronous dialog.
 */
public final class FileDialogs {
    private static final String[] IMAGE_EXTENSIONS = {"png", "jpg", "jpeg", "gif", "webp"};

    private FileDialogs() {
    }

    /**
     * Lets the player pick image files. Call on the render thread; {@code onResult} runs on some
     * other thread with the chosen files (empty when cancelled).
     */
    public static void openImages(String title, String filterName, Consumer<List<Path>> onResult) {
        //? if >=26.3 {
        openSdl(filterName, onResult);
        //?} else {
        /*Thread thread = new Thread(() -> onResult.accept(openTinyfd(title, filterName)), "CraftProxyFileDialog");
        thread.setDaemon(true);
        thread.start();
        *///?}
    }

    //? if >=26.3 {
    private static void openSdl(String filterName, Consumer<List<Path>> onResult) {
        // SDL reads the filters when the dialog shows up, which may be after this method returned:
        // keep them in native memory until the callback ran.
        ByteBuffer name = MemoryUtil.memUTF8(filterName);
        ByteBuffer pattern = MemoryUtil.memUTF8(String.join(";", IMAGE_EXTENSIONS));
        SDL_DialogFileFilter.Buffer filters = SDL_DialogFileFilter.calloc(1);
        filters.get(0).name(name).pattern(pattern);

        SDL_DialogFileCallback[] self = new SDL_DialogFileCallback[1];
        self[0] = SDL_DialogFileCallback.create((userdata, fileList, filter) -> {
            List<Path> paths = new ArrayList<>();
            if (fileList != MemoryUtil.NULL) {
                for (int i = 0; ; i++) {
                    long entry = MemoryUtil.memGetAddress(fileList + (long) i * Pointer.POINTER_SIZE);
                    if (entry == MemoryUtil.NULL) break;
                    paths.add(Path.of(MemoryUtil.memUTF8(entry)));
                }
            }
            // The closure must not free itself while it is still running.
            Minecraft.getInstance().execute(() -> {
                self[0].free();
                filters.free();
                MemoryUtil.memFree(name);
                MemoryUtil.memFree(pattern);
            });
            onResult.accept(paths);
        });
        long window = Minecraft.getInstance().getWindow().handle();
        SDLDialog.SDL_ShowOpenFileDialog(self[0], MemoryUtil.NULL, window, filters, (CharSequence) null, true);
    }
    //?} else {
    /*private static List<Path> openTinyfd(String title, String filterName) {
        String result;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer patterns = stack.mallocPointer(IMAGE_EXTENSIONS.length);
            for (String extension : IMAGE_EXTENSIONS) {
                patterns.put(stack.UTF8("*." + extension));
            }
            patterns.flip();
            result = TinyFileDialogs.tinyfd_openFileDialog(title, null, patterns, filterName, true);
        } catch (Throwable e) {
            System.out.println("[CraftProxy] File dialog failed: " + e.getMessage());
            return List.of();
        }
        List<Path> paths = new ArrayList<>();
        if (result != null) {
            for (String part : result.split("\\|")) {
                if (!part.isBlank()) paths.add(Path.of(part));
            }
        }
        return paths;
    }
    *///?}
}
