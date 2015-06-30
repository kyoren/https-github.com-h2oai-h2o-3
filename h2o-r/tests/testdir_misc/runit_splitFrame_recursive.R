###############################################
##
## Test corner case splits through recursion
##
###############################################


setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.splitframe.recursion <- function(conn) {

  hex <- as.h2o(iris)

  rec_split <- function(fr) {
    splits <- h2o.splitFrame(fr, 0.5)
    p1 <- splits[[1]]
    p2 <- splits[[2]]
    if (nrow(p1) > 1)
      rec_split(p1)
    if (nrow(p2) > 1 && nrow(p2) != nrow(p1))
      rec_split(p2)
  }

  rec_split(h2o.splitFrame(hex, 0.5)[[1]])

  testEnd()
}

doTest("Recursive Application of Split Frame", test.splitframe.recursion)