# Simulation batches

```
python3 -m venv --without-pip .venv
curl -sS https://bootstrap.pypa.io/get-pip.py | .venv/bin/python
source .venv/bin/activate
pip install -e .
pip install -U -r requirements.txt
```

## Testing
pip install nose
python3 -m nose test
