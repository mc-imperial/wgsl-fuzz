
enable subgroups;
@compute @workgroup_size(16,1,1)
fn main(@builtin(subgroup_invocation_id) p : u32) {
  if p > 0 {
    workgroupBarrier();
  }
}
