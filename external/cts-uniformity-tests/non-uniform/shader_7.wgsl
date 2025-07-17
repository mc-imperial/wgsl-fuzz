

@compute @workgroup_size(16,1,1)
fn main(@builtin(global_invocation_id) p : vec3<u32>) {
  if p.x > 0 {
    workgroupBarrier();
  }
}
