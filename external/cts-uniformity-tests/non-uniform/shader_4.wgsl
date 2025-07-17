

@compute @workgroup_size(16,1,1)
fn main(@builtin(local_invocation_id) p : vec3<f32>) {
  if p.x > 0 {
    workgroupBarrier();
  }
}
