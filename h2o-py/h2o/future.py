class H2OFuture(object):
    """
    A class representing a future results of asynchronous job.
    """
    def __init__(self, job, x):
        self.job = job
        self.x = x

    def poll(self):
        self.job.poll()
        self.x = None
