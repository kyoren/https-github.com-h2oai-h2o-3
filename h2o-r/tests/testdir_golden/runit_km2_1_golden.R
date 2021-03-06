setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

# Compare within-cluster sum of squared error
test.kmvanilla.golden <- function() {
  # Import data: 
  Log.info("Importing ozone.csv data...") 
  ozoneR <- read.csv(locate("smalldata/glm_test/ozone.csv"), header = TRUE)
  ozoneH2O <- h2o.uploadFile(locate("smalldata/glm_test/ozone.csv"), destination_frame = "ozoneH2O")
  startIdx <- sort(sample(1:nrow(ozoneR), 3))
  
  Log.info("Initial cluster centers:"); print(ozoneR[startIdx,])
  fitR <- kmeans(ozoneR, centers = ozoneR[startIdx,], iter.max = 1000, algorithm = "Lloyd")
  fitH2O <- h2o.kmeans(ozoneH2O, init = ozoneH2O[startIdx,], standardize = FALSE)
  checkKMeansModel(fitH2O, fitR, tol = 0.01)
  
  Log.info("Compare Predicted Classes between R and H2O\n")
  classR <- fitted(fitR, method = "classes")
  classH2O <- predict(fitH2O, ozoneH2O)
  expect_equivalent(as.numeric(as.matrix(classH2O))+1, classR)   # H2O indexes from 0, but R indexes from 1
  
  
}

doTest("KMeans Test: Golden Kmeans - Ozone without Standardization", test.kmvanilla.golden)
