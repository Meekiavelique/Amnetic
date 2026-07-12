# Compute Shaders


Amnetic make your mod run **compute shaders** inside Minecraft.
But.. What is a compute shader?

A compute shader is not part of the draw pipeline. It doesn't have vertices and pixels. It just runs a function in parallel across a lot of GPU threads, it reads and write buffers and textures.
They are mostly for stuff that will struggle on the CPU (particle simulations, mesh deformation, image filters, physics etc).

---

## Before you start: the context requirement

Compute shaders require **OpenGL 4.3** (or the `ARB_compute_shader` extension). Vanilla Minecraft requests a 3.3 context, where compute does not exist. This results in the function pointers being null.

Amnetic works around this by requesting a **4.6 core** context *when the game window is created* (it has to happen then; you cannot upgrade a live context). The requested version is overridable with the `amnetic.opengl.major` / `amnetic.opengl.minor` system properties, and if creation fails it falls back to a plain 3.3 context. On the vast majority of desktop GPUs the 4.6 request succeeds. But some drivers or headless setups won't give you a compute-capable context, so **there is no guarantee** and you must check at runtime:

```java
if (!ComputeCapabilities.isComputeAvailable()) {
    // No compute on this machine
    return;
}
```
Amnetic probes the capabilities on the first client tick. Before that probe runs, `isComputeAvailable()` returns `false`, so do not gate anything on it in your mod initializer. By the time your gameplay code starts running in a world, it already has an answer.
Everything that has to do with compute must run on the **render thread** with the GL context current.
For example you can run it inside a render event.
You should not run it on a worker thread.


## The mental model

When you use a compute shader it says how many threads will run together, which is called the *work-group size*. You set this with `local_size_x/y/z`. When you start the shader, which is called *dispatch*, you say how many **groups** you want to launch. To figure out the number of threads you multiply the number of **groups** by the **work-group size**.

Let's say you have 4096 pieces of data and your shader uses `local_size_x = 64`. In this case you would start 64 **groups**. Each thread would handle one piece of data. Inside the shader, `gl_GlobalInvocationID.x` tells the thread its index, which is a number from 0 to 4095.

```glsl
#version 430
layout(local_size_x = 64) in; // 64 threads per group
layout(std430, binding = 0) buffer Data { float values[]; };
void main() {
    uint i = gl_GlobalInvocationID.x; // which piece of data am I working with?
    values[i] = values[i] * 2.0; // do the work on this piece of data
}
```

The **compute shader** uses a **Shader Storage Buffer Object (SSBO)** to move data between the CPU and the GPU. This is like a block of memory on the GPU that is linked to a specific slot, like `binding = 0` in the example above. You upload your data, start the *compute shader*, add a **memory barrier** so that the changes the GPU made are visible, and then either read the result back to the CPU or leave it on the GPU to use later.

The **memory barrier** step is important because it makes sure the GPU's changes are visible to the CPU or other parts of the GPU. You have to call `glMemoryBarrier(...)` with the right flags to make this happen. People often forget this step. The **compute shader** and the **SSBO** work together to make this process work.

---
## Example

This is an example that does a calculation on the computer's graphics card. It takes 256 numbers, doubles each one, and then reads the results back.

**1. The shader.** This is a program that runs on the graphics card. It is stored in a file called `double.comp` in the `assets/mymod/shaders/compute` directory.

```glsl
#version 430
layout(local_size_x = 64) in;
layout(std430, binding = 0) buffer Output {
    float data[];
};
void main() {
    uint i = gl_GlobalInvocationID.x;
    data[i] = float(i) * 2.0;
}
```

**2. The Java code.** This is the code that runs on the computer and tells the graphics card what to do. It loads the shader, sets up the data, runs the calculation, and then reads the results back.

```java
import com.meekdev.amnetic.client.compute.*;
import net.minecraft.resources.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL43;
import java.nio.FloatBuffer;

void runDoubleKernel() {
    if (!ComputeCapabilities.isComputeAvailable()) return;
    int count = 256;
    try (ComputeShader shader = ComputeShader.load(Identifier.fromNamespaceAndPath("mymod", "shaders/compute/double.comp"));
         ShaderStorageBuffer ssbo = new ShaderStorageBuffer((long) count * Float.BYTES)) {
        ssbo.bind(0);
        shader.dispatch(count / 64, 1, 1);
        ComputeShader.barrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
        FloatBuffer out = BufferUtils.createFloatBuffer(count);
        ssbo.readInto(out);
    }
}
```

That is the process: load the shader, set up the data, run the calculation, wait for the graphics card to finish, and then read the results back. If you are only using the graphics card and not reading the results back, you can skip that step.

---

## Reference

### ComputeCapabilities

This checks if the computer's graphics card can do the calculations we need. It also logs some information about the graphics card.

```java
ComputeCapabilities.probeOnce();
ok = ComputeCapabilities.isComputeAvailable();
```

`isComputeAvailable()` returns `true` if the graphics card can do the calculations. The probe logs some information about the graphics card, which is useful for debugging.

### ComputeShader

This is a compute program. It owns a GL program object.

```java
ComputeShader load(Identifier sourceId)
void use()
void dispatch(int gx, int gy, int gz)
static void barrier(int barrierBits)
// uniforms
void setInt(String name, int v)
void setFloat(String name, float v)
void setVec3(String name, float x, float y, float z)
void setMatrix4(String name, Matrix4fc m)
void setTexture(String name, int unit, int glTextureId)
int program()
void close()
```

**Loading** reads the source from the exact resource path. This is different from the post-effect API. The program compiles once at `load()` and never recompiles; if you use shader hot-reload during development, recreate the `ComputeShader` from a `ShaderHotReload.onReload(...)` callback to pick up edits.

**Uniforms**: the setters write to whatever program is currently bound, so call `use()` before setting them. Only `dispatch(...)` binds the program for you.

**Barriers**: pass the bit matching what you will do next.

**Textures**: `setTexture(name, unit, computeTexture.id())` activates the unit, binds the texture, and points the sampler at it in one call.

### ShaderStorageBuffer

This is GPU memory for data shared with the shader via a numbered binding.

```java
new ShaderStorageBuffer(long sizeBytes)
void bind(int binding)
void upload(FloatBuffer data)
void readInto(FloatBuffer dest)
long sizeBytes();  int id();  void close();
```

The `binding` you pass to `bind()` is the number as in the shader's `layout(std430, binding = N)`.

### ComputeTexture

This is a 2D texture loaded from a PNG for sampling inside a compute shader.

```java
ComputeTexture load(Identifier pngId)
int id();  int width();  int height();  void close();
```

The PNG is uploaded as **RGBA8** with **repeat** wrap and **trilinear** filtering.

---

## Threading

- **Every compute object is a GL resource.** Use try-with-resources for one-shot work. Hold them in fields and `close()` them on teardown.
- **Render thread only.** Create, dispatch, and read on the render thread while the GL context is current.

---

## See also

