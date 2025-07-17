
struct block {
      x : u32,
      y : u32
    }

@group(0) @binding(0)
var<storage> uniform_value : array<block, 4>;
@group(0) @binding(1)
var<storage, read_write> nonuniform_value : array<block, 4>;

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
  var x : block = nonuniform_value[3];;

  for (; uniform_cond; ) {
      if uniform_cond {
        x = uniform_value[0];
        break;
        x.y = nonuniform_value[0].y;
      } else {
        if uniform_cond {
          continue;
        }
        x = uniform_value[1];
      }
    }

  if x.x > 0 {
    let tmp = textureSample(t, s, vec2f(0,0));
  }
}
