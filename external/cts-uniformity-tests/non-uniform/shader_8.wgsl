

@compute @workgroup_size(16,1,1)
fn main(@builtin(local_invocation_index) p : u32) {
  if p > 0 {
    workgroupBarrier();
  }
}
