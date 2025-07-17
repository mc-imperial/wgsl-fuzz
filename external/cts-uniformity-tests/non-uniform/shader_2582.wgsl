
@group(0) @binding(0)
var t : texture_2d<f32>;
@group(0) @binding(1)
var s : sampler;
@group(0) @binding(2)
var td : texture_depth_2d;
@group(0) @binding(3)
var sd : sampler_comparison;
@group(0) @binding(4)
var ta : texture_2d_array<f32>;
@group(0) @binding(5)
var ts : texture_multisampled_2d<f32>;

const uniform_cond = true;
var<private> nonuniform_cond = true;

@group(1) @binding(0)
var<storage> uniform_values : array<u32, 4>;
@group(1) @binding(1)
var<storage, read_write> nonuniform_values : array<u32, 4>;

@diagnostic(warning, derivative_uniformity)
    fn foo() -> vec4f {
      return textureSample(t,s,vec2f(0,0));
    }

@fragment
fn main() {
  if nonuniform_cond {
      let call = foo();
      let sample = textureSample(t,s,vec2f(0,0));
    }

  if uniform_cond {
    let tmp = textureSample(t,s,vec2f(0,0));
  }
}
