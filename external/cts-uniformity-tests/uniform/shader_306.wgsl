
@group(0) @binding(0)
var t : texture_2d<f32>;
@group(0) @binding(1)
var s : sampler;

const uniform_cond = true;
var<private> nonuniform_cond = true;

@group(1) @binding(0)
var<storage> uniform_values : array<u32, 4>;
@group(1) @binding(1)
var<storage, read_write> nonuniform_values : array<u32, 4>;

fn foo(p : ptr<function, u32>) {
      *p = uniform_values[0];
    }

@fragment
fn main(@builtin(position) pos : vec4f) {
  var x = nonuniform_values[0];
    foo(&x);

  if x > 0 {
    let tmp = textureSample(t,s,vec2f(0,0));
  }
}
