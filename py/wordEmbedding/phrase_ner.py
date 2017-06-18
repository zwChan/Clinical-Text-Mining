from __future__ import division,print_function
__author__ = 'Jason'
import sys


def create_vocab(vcfile):
    vocab = set()
    with open(vcfile) as f:
        for line in f.readlines():
            if line.count('_') == 0: continue
            vocab.add(line.split()[0].lower().strip())
    return vocab

def connect_phrass_line(line,vocab, ngram=4):
    tokens = line.split()
    i = ngram
    while i > 1:
        j = 0
        tokens_tmp = []
        while j < len(tokens) - i + 1:
            phrase = '_'.join(tokens[j:j+i])
            if phrase.lower() in vocab:
                tokens_tmp.append(phrase)
                j += i
            else:
                tokens_tmp.append(tokens[j])
                j += 1
        i -= 1
        tokens = tokens_tmp
    return ' '.join(tokens)

def connect_phrase(infile, vocab, ngram=4):
    with open(infile) as f:
        for line in f.readlines():
            print(connect_phrass_line(line,vocab,ngram))

# ----------------------------
if len(sys.argv) < 3:
    print("Usage: [token-file] [vocab-file]",file=sys.stderr)
    exit(1)
infile = sys.argv[1]
vcfile = sys.argv[2]
vocab = create_vocab(vcfile)
# print(vocab)
connect_phrase(infile,vocab)

