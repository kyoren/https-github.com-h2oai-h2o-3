setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.headers <- function() {
  
  Log.info("Create date data")
  data      <- data.frame( ID = c(1,2,3,4), Date = c("1984-05-27", "2005-07-07", "1960-01-01", "1970-01-01"))
  data$Date <- as.Date(data$Date)
  diff1     <- (data[4,2] - data[3,2])

  Log.info("Import created date data...")
  data.hex  <- as.h2o(data)
  diff2     <- as.numeric(as.matrix(data.hex[4,2] - data.hex[3,2])/(1000*60*60*24))
  checkEqualsNumeric(diff1, diff2)

  
}

doTest("Import a dataset with a header H2OParsedData Object", test.headers)
