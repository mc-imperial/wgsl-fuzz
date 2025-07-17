
@group(1) @binding(0)
var t : texture_2d<f32>;
@group(1) @binding(1)
var s : sampler;

const uniform_cond = true;
var<private> nonuniform_cond = false;

@fragment
fn main() {
  
      let x = textureSample(t, s, vec2f(0,0)).x == 0 || nonuniform_cond;
    
}
