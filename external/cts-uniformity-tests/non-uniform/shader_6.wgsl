
enable subgroups;
@group(0) @binding(0) var s : sampler;
@group(0) @binding(1) var tex : texture_2d<f32>;

@fragment
fn main(@builtin(subgroup_size) p : u32) {
  if p > 0 {
    let texel = textureSample(tex, s, vec2<f32>(0,0));
  }
}
