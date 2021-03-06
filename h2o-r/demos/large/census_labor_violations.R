## Set your working directory
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))

## Load library and initialize h2o
library(h2o)
print("Launching H2O and initializing connection object...")
conn <- h2o.init(nthreads = -1)

## Find and import data into H2O
locate       <- h2o:::.h2o.locate
pathToACSData   <- locate("bigdata/laptop/census/ACS_13_5YR_DP02_cleaned.zip")
pathToWHDData   <- locate("bigdata/laptop/census/whd_zcta_cleaned.zip")

print("Importing ACS 2013 5-year DP02 demographic dataset into H2O...")
acs_orig <- h2o.uploadFile(pathToACSData, col.types = c("enum", rep("numeric", 149)))

## Save and drop zip code column from training frame
acs_zcta_col <- acs_orig$ZCTA5
acs_full <- acs_orig[,-which(colnames(acs_orig) == "ZCTA5")]
summary(acs_full)

print("Importing WHD 2014-2015 labor violations dataset into H2O...")
whd_zcta <- h2o.uploadFile(pathToWHDData, col.types = c(rep("enum", 7), rep("numeric", 97)))
dim(whd_zcta)
summary(whd_zcta)

print("Run GLRM to reduce ZCTA demographics to k = 5 archetypes")
acs_model <- h2o.glrm(training_frame = acs_full, k = 5, transform = "STANDARDIZE", init = "PlusPlus",
                      loss = "Quadratic", max_iterations = 100, regularization_x = "Quadratic",
                      regularization_y = "L1", gamma_x = 0.25, gamma_y = 0.5)
acs_model

## Embedding of ZCTAs into archetypes (X)
zcta_arch_x <- h2o.getFrame(acs_model@model$loading_key$name)
head(zcta_arch_x)

## Archetype to full feature mapping (Y)
arch_feat_y <- acs_model@model$archetypes
arch_feat_y

## Split WHD data into test/train with 20/80 ratio
split <- h2o.runif(whd_zcta)
train <- whd_zcta[split <= 0.8,]
test  <- whd_zcta[split > 0.8,]

print("Build a GBM model on original WHD data to predict repeat violators")
myY <- "flsa_repeat_violator"
myX <- setdiff(4:ncol(train), which(colnames(train) == myY))
orig_time <- system.time(gbm_orig <- h2o.gbm(x = myX, y = myY, training_frame = train, validation_frame = test, 
                                             ntrees = 10, max_depth = 6, distribution = "multinomial"))

print("Replace ZCTA5 column in WHD data with GLRM archetypes")
zcta_arch_x$zcta5_cd <- acs_zcta_col
whd_arch <- h2o.merge(whd_zcta, zcta_arch_x, all.x = TRUE, all.y = FALSE)
whd_arch$zcta5_cd <- NULL
summary(whd_arch)

## Split modified WHD data into test/train with 20/80 ratio
train_mod <- whd_arch[split <= 0.8,]
test_mod  <- whd_arch[split > 0.8,]

print("Build a GBM model on modified WHD data to predict repeat violators")
myX <- setdiff(4:ncol(train_mod), which(colnames(train_mod) == myY))
mod_time <- system.time(gbm_mod <- h2o.gbm(x = myX, y = myY, training_frame = train_mod, validation_frame = test_mod, 
                                           ntrees = 10, max_depth = 6, distribution = "multinomial"))

print("Performance comparison:")
data.frame(original  = c(orig_time[3], gbm_orig@model$training_metric@metrics$MSE, gbm_orig@model$validation_metric@metrics$MSE),
           reduced   = c(mod_time[3], gbm_mod@model$training_metric@metrics$MSE, gbm_mod@model$validation_metric@metrics$MSE),
           row.names = c("runtime", "train_mse", "test_mse"))
