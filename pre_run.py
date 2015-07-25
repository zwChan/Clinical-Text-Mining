#!/usr/bin/python

'''
 Prepare the data for the project, for now, just unzip the files.
 I don't find the way to find the exact type of the file, but I know it can be decompressed by gzip.
 So I call the shell command "gzip -d -f" to do it.
'''
import os
import gzip  

def walk_dir(dir,topdown=True):
    for root, dirs, files in os.walk(dir, topdown):
        for name in files:
            print name
            if not name.endswith(".Z"): continue
            print("unzip file: " + os.path.join(root,name))
            os.system("gzip -d -f " + os.path.join(root,name))
           

walk_dir("./data")


