from __future__ import division,print_function
__author__ = 'Jason'
import sys,re,json,yaml,time
from stanfordcorenlp import StanfordCoreNLP

print(sys.argv)
if len(sys.argv) < 4:
    print("Usage: [input_file.csv] [stanfordNlp3.7_path] [output_file] [file_type, analogy or relation]")
    exit(1)
input_file = sys.argv[1]
stanfordNlpPath = sys.argv[2]
output_file = sys.argv[3]
file_type = sys.argv[4] if len(sys.argv) > 4 else 'analogy'
nlp = StanfordCoreNLP(stanfordNlpPath)

def pos_transform(pos):
    nounPos = "NN NNS NNP NNPS".split()
    if  pos in nounPos:
      return "N" # noun
    elif (pos == "JJ"  or  pos == "JJR"  or  pos == "JJS"):
      return "A" # adjective
    elif (pos == "IN"):
      return "P" # preposition or a conjunction that introduces a subordinate clause, e.g., although, because.
    elif (pos == "RB" or pos == "RBR" or pos == "RBS" or  pos=="WRB"):
      return "R" # Adverb
    elif (pos == "VB" or pos == "VBD" or  pos=="VBP" or pos=="VBZ"):
      return "V" # verb
    elif (pos == "VBG"):
      return "G"
    elif (pos == "VBN"):
      return "B"
    elif (pos == "TO"):
      return "T" # to
    elif ("DT" ==pos or "PDT"==pos or "WDT"==pos):
      return "D" # determiner
    elif (pos == "EX"):
      return "E" # existential
    elif (pos == "FW"):
      return "F" # foreign word
    elif (pos == "CD"):
      return "M" # cardinal number
    elif (pos == "RPR" or pos == "RPR$" or pos == "WP" or pos == "WP$"):
      return "U" # pronoun
    elif (pos == "CC"):
      return "C" # a conjunction placed between words, phrases, clauses, or sentences of equal rank, e.g., and, but, or.
    elif (pos == "UH"):
      return "W" # interjection
    elif (pos == "X"):
      return "X" # if the sentence is too long, stanford nlp will return 'X', meaning no parsing.
    # elif (punctPattern.matcher(pos).matches()):
    #   "Z" # keep the input if it is a punctuation
    else:
      return "O" # others


def pos_lemma(text):
    global nlp
    props={'annotators': 'pos,lemma','pipelineLanguage':'en','outputFormat':'json'}
    r_dict = nlp.annotate(text, properties=props)
    r_dict = yaml.safe_load(r_dict)
    pos_lemma = []
    for s in r_dict['sentences']:
        for token in s['tokens']:
            pos_lemma.append("%s|%s" % (pos_transform(token['pos'].strip()),token['lemma']))
    return pos_lemma

def tag_word_analogy(infile, outfile):
    with open(outfile,'w+') as of:
        with open(infile) as f:
            for line in f.readlines():
                if line.strip().startswith(':'):
                    print(line.strip(),file=of)
                    continue
                ret = []
                words = line.split()
                for w in words:
                    pos_w = pos_lemma(w.replace('_',' '))
                    ret.append('_'.join(pos_w))
                print(' '.join(ret),file=of)

def tag_word_relation(infile, outfile):
    with open(outfile,'w+') as of:
        with open(infile) as f:
            first_line = True
            start_time = time.time()
            cnt = 0
            for line in f.readlines():
                if first_line:
                    print(line.strip(),file=of)
                    first_line = False
                    continue
                ret = []
                columns = line.split('\t')
                first_column = True
                for col in columns:
                    if first_column:
                        ret.append(col)
                        first_column = False
                    else:
                        sep = ','
                        words_ret = []
                        for w in col.split(sep):
                            pos_w = pos_lemma(w.replace('_',' '))
                            words_ret.append('_'.join(pos_w))
                        ret.append(sep.join(words_ret))
                        cnt += 1
                        if cnt % 1000 == 0:
                            print("\r cnt %d, time elapsed: %d" % (cnt, int(time.time() - start_time)), file=sys.stderr)
                print('\t'.join(ret),file=of)

if file_type == 'relation':
    tag_word_relation(input_file, output_file)
else:
    tag_word_analogy(input_file, output_file)