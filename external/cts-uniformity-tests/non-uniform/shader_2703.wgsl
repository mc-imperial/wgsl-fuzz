
 enable subgroups;

 @group(0) @binding(0) var s : sampler;
 @group(0) @binding(1) var s_comp : sampler_comparison;
 @group(0) @binding(2) var tex : texture_2d<f32>;
 @group(0) @binding(3) var tex_depth : texture_depth_2d;

 @group(1) @binding(0) var<storage, read> ro_buffer : array<f32, 4>;
 @group(1) @binding(1) var<storage, read_write> rw_buffer : array<f32, 4>;
 @group(1) @binding(2) var<uniform> uniform_buffer : vec4<f32>;

 @group(2) @binding(0) var ro_storage_texture : texture_storage_2d<rgba8unorm, read>;
 @group(2) @binding(1) var rw_storage_texture : texture_storage_2d<rgba8unorm, read_write>;

 var<private> priv_var : array<f32, 4> = array(0,0,0,0);

 const c = false;
 override o : f32;
@fragment
fn main(@builtin(position) p : vec4<f32>) {
      let u_let = uniform_buffer.x;
      let n_let = rw_buffer[0];
      var u_f = uniform_buffer.z;
      var n_f = rw_buffer[1];
    if ro_buffer[priv_var[0]] == 0 {
        let x = subgroupExclusiveAdd(0);
;
      }
      
}
