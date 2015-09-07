from future import H2OFuture

class H2OGridFuture(H2OFuture):
    """
    A class representing a future H2O grid 
    """
    def __init__(self, job, x):
        super(H2OModelFuture, self).__init(job, x)

