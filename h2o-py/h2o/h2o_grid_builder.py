import h2o_model_builder
from connection import H2OConnection
from job        import H2OJob
from model.model_future import H2OModelFuture

def build_grid(algo, kwargs):
    return _build_grid(algo, **kwargs)


def _build_grid(algo, kwargs):
    do_future = kwargs.pop("do_future") if "do_future" in kwargs else False
    future_grid = H2OGridFuture(H2OJob(H2OConnection.post_json("Grids/"+algo, **kwargs), job_type=(algo + " Grid"), None))
    return future_grid if do_future else _resolve_grid(future_grid, **kwargs)

def _resolve_grid(future_grid, **kwargs):
    future_grid.poll()
    if '_rest_version' in kwargs.keys(): grid_json = H2OConnection.get_json("Grids/"+future_grid.job.dest_key, _rest_version=kwargs['_rest_version'])
    else:                                grid_json = H2OConnection.get_json("Grids/"+future_gird.job.dest_key)
    return Grid(future_grid.dest_key, grid_json)

