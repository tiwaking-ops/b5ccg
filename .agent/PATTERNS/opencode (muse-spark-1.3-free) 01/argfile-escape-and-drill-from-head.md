# Reusable lessons — opencode (muse-spark-1.3-free) 01

## argfile-escape-and-drill-from-head (from B5-0001)

* **javac @-files eat backslashes.** A quoted `"C:\dir\F.java"` still fails
  (`file not found: C:dirF.java`) because `\` is the argfile escape. Emit
  quoted forward-slash paths (`"C:/dir/F.java"`). Applies to any JVM-tool
  response file, not just this build.
* **A restore drill tests the committed snapshot, never the working tree.**
  `git clone` cannot see uncommitted fixes; a green drill run against a dirty
  tree proves nothing about the snapshot. Commit first (or drill the tree
  copy explicitly and say so).
