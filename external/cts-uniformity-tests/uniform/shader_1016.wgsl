
@group(0) @binding(0)
var t : texture_2d<f32>;
@group(0) @binding(1)
var s : sampler;

struct S {
  x : u32
}

const uniform_struct = S(1);
var<private> nonuniform_struct = S(1);

const uniform_value : array<u32, 2> = array(1,1);
var<private> nonuniform_value : array<u32, 2> = array(1,1);

const uniform_val : u32 = 1;
var<private> nonuniform_val : u32 = 1;

@fragment
fn main() {
  let tmp = uniform_value[uniform_val] | uniform_val;
  if tmp > 0 {
    let res = textureSample(t, s, vec2f(0,0));
  }
}
