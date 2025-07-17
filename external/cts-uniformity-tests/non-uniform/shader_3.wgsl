

@group(0) @binding(0) var s : sampler;
@group(0) @binding(1) var tex : texture_2d<f32>;

@fragment
fn main(@builtin(front_facing) p : bool) {
  if p {
    let texel = textureSample(tex, s, vec2<f32>(0,0));
  }
}
