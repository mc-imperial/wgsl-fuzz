
@group(0) @binding(0)
var t : texture_2d<f32>;
@group(0) @binding(1)
var s : sampler;

struct S {
  x : i32
}

const uniform_struct = S(1);
var<private> nonuniform_struct = S(1);

const uniform_value : array<i32, 2> = array(1,1);
var<private> nonuniform_value : array<i32, 2> = array(1,1);

const uniform_val : i32 = 1;
var<private> nonuniform_val : i32 = 1;

@fragment
fn main() {
  let i_tmp = uniform_value[uniform_val];
  let b_tmp = bool(i_tmp);
  let tmp = !b_tmp;
  if tmp {
    let res = textureSample(t, s, vec2f(0,0));
  }
}
