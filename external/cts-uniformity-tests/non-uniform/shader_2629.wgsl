
enable subgroups;

var<private> non_uniform : u32 = 0;

@group(0) @binding(0)
var<storage> uniform : u32;

@compute @workgroup_size(16,1,1)
fn main() {
  let x = subgroupShuffleXor(non_uniform, non_uniform);
}