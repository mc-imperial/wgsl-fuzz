


@group(0) @binding(0)
var<storage> uniform_value : array<u32, 4>;
@group(0) @binding(1)
var<storage, read_write> nonuniform_value : array<u32, 4>;

@group(1) @binding(0)
var t : texture_2d<f32>;
@group(1) @binding(1)
var s : sampler;

var<private> nonuniform_cond : bool = true;
const uniform_cond : bool = true;
var<private> nonuniform_val : u32 = 0;
const uniform_val : u32 = 0;

@fragment
fn main() {
  var x : u32 ;

  if uniform_cond {
      x = uniform_value[0];
    } else {
      x = nonuniform_value[0];
    }

  if x > 0 {
    let tmp = textureSample(t, s, vec2f(0,0));
  }
}
