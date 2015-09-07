"""
This module provides representation of grid object.

The formatting follows PEP-0008 - http://www.python.org/dev/peps/pep-0008/
"""

class Grid(objec):
    def __init__(self, dest_key, grid_json):
        self._id = dest_key
        self._grid_json = grid_json

    @property
    def id(self):
        """
        :return: Retrieve this grid identifier
        """
        return self._id

