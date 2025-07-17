
var<workgroup> wg_scalar : u32;
var<workgroup> wg_array : array<u32, 16>;
var<workgroup> wg_atomic : atomic<u32>;

struct Inner {
  x : array<u32, 4>
}
struct Middle {
  x : array<Inner, 4>
}
struct Outer {
  x : array<Middle, 4>
}
var<workgroup> wg_struct : Outer;

@group(0) @binding(0)
var<storage> uniform_value : u32;
@group(0) @binding(1)
var<storage, read_write> nonuniform_value : u32;

fn needs_uniform(val : u32) -> u32{
  if val == 0 {
    workgroupBarrier();
  }
  return val;
}

@compute @workgroup_size(16, 1, 1)
fn main(@builtin(local_invocation_id) lid : vec3<u32>,
        @builtin(global_invocation_id) gid : vec3<u32>) {
  var func_scalar : u32;
  var func_vector : vec4u;
  var func_array : array<u32, 16>;
  var func_struct : Outer;

  *&(func_array[needs_uniform(uniform_value)]) = uniform_value;
    let test_val = func_array[0];

if test_val > 0 {
      workgroupBarrier();
    }
}