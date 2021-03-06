#----------------------------------------------------------------------
# Purpose:  This test exercises HDFS operations from R.
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit-hadoop.R')

ipPort <- get_args(commandArgs(trailingOnly = TRUE))
myIP   <- ipPort[[1]]
myPort <- ipPort[[2]]
hdfs_name_node <- Sys.getenv(c("NAME_NODE"))
print(hdfs_name_node)

library(RCurl)
library(h2o)
library(testthat)

#----------------------------------------------------------------------

heading("BEGIN TEST")
h2o.init(ip=myIP, port=myPort, startH2O = FALSE)

hdfs_data_file = "/datasets/runit/BigCross.data"

url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_data_file)
data.hex <- h2o.importFile(url)
print(summary(data.hex))

myY = "C1"
myX = setdiff(names(data.hex), myY)

# GLM
data.glm <- h2o.glm(myX, myY, training_frame = data.hex, family = "gaussian", solver = "L_BFGS")
data.glm

# GBM Model
data.gbm <- h2o.gbm(myX, myY, training_frame = data.hex, distribution = 'AUTO')
print(data.gbm)

# DL Model
data.dl  <- h2o.deeplearning(myX, myY, training_frame = data.hex, epochs=1, hidden=c(50,50), loss = 'Automatic')
print(data.dl)

PASS_BANNER()
