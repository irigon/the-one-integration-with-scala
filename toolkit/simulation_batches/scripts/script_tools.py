import os
import itertools

# return True if option is a list 
def is_list():
	pass

# return True if option is a sequence to be generated
def is_iterator():
	pass

# read configuration and return a dictionary of options
def read_config(conf_path):
    dic={}
    with open(conf_path, 'r') as f:
        for line in f.readlines():
            if not line.startswith('#'):
                k,v = line.strip().split('=')
                dic[k] = v.split(';')
                if 'range' in dic[k]:
                    dic[k] = range(dic[k][1:])
    return dic

# receive dict in which value is a list
# return a list of dicts in which each value is a item of the cartesian product of the
# values from the input

# ex.: in: dic('a':[1,2], 'b':[3,4]) --> out([
#   dic('a':1, 'b':3),
#   dic('a':1, 'b':4),
#   dic('a':2, 'b':3),
#   dic('a':2, 'b':4),
# ])
def product(dict):
    out = []
    k = dict.keys()
    product = itertools.product(*dict.values())
    for element in product:
        out.append({x:y for x, y in zip(k, element)})
    return out


# a match occurs when after name there is either ' ' or '='
def matchName(line, name):
    next_char = None
    if line.startswith(name):
        try:
            next_char = line[len(name)]
        except:
            return False
    return next_char in [' ', '=', '']

def setValues(currFilePath, varName, varValue):
    fName = currFilePath.split('/')[-1]
    tmpName = '/tmp/'+fName
    with open(currFilePath, 'r') as f:
        with open(tmpName, 'w') as out:
            for line in f:
                if matchName(line, varName):
                    out.write('{} = {}\n'.format(varName, varValue))
                else:
                    out.write(line)
    os.rename(tmpName, currFilePath)

