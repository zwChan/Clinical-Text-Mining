from __future__ import division,print_function
__author__ = 'Jason'

import sys
import gensim

if len(sys.argv) <= 3:
    print("Usage: [model-file] [vocab-file] [top-k] ",file=sys.stderr)
    exit(1)
print('\t'.join(sys.argv))
model = sys.argv[1]
# model = r'C:\fsu\class\thesis\token.txt.bin'
vocFile=sys.argv[2]
# vocFile = r'C:\fsu\class\thesis\token.txt.voc'
topn = 10 if len(sys.argv) <= 3 else int(sys.argv[3])
print("Loading model...")
wv = gensim.models.KeyedVectors.load_word2vec_format(model,fvocab=vocFile,binary=True)
print("model loaded.")
while True:
    line = raw_input("Input a term to find similar terms:").strip()
    try:
        sims = wv.most_similar(line.split(),topn=topn)
        print ("top %d similar terms for %s:" % (topn, line))
        print('\n'.join(["%s\t%0.4f" % t for t in sims]))
    except Exception as e:
        print("Error: %s" % e.message)