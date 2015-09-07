from job.future import H2OFuture

class H2OModelFuture(H2OFuture):
    """
    A class representing a future H2O model (a model that may, or may not, be in the process of being built)
    """
    def __init__(self, job, x):
        super(H2OModelFuture, self).__init(job, x)

