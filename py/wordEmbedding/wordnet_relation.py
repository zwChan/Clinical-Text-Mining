from __future__ import division,print_function
__author__ = 'Jason'
import csv,sys,re,json,yaml
from stanfordcorenlp import StanfordCoreNLP

################
# Obtain synonym from wordnet. docs: http://www.nltk.org/howto/wordnet.html
from nltk.corpus import wordnet as wn
S = wn.synset
L = wn.lemma


print(sys.argv)
if len(sys.argv) < 5:
    print("Usage: [input_file.csv] [output_file] [wordset_file] [stanfordNlp3.7_path]")
    exit(1)
input_file = sys.argv[1]
output_file = sys.argv[2]
wordset_file = sys.argv[3]
stanfordNlpPath = sys.argv[4]
nlp = StanfordCoreNLP(stanfordNlpPath)

def lemma(text):
    global nlp
    props={'annotators': 'lemma','pipelineLanguage':'en','outputFormat':'json'}
    r_dict = nlp.annotate(text, properties=props)
    r_dict = yaml.safe_load(r_dict)
    lemma = []
    for s in r_dict['sentences']:
        for token in s['tokens']:
            lemma.append(token['lemma'])
    return lemma

def get_synonyms(term,pos='asn'):
    synonym = set()
    for ss in wn.synsets(term.strip(),pos=pos):
        #print(ss.name(), ss.lemma_names())
        #synonym.add(ss.name().lower())
        for lemma in ss.lemma_names():
            synonym.add(lemma.lower())
    # for s in synonym:
    #     print(s)
    synonym.discard(term)
    return synonym
def get_antonyms(term,pos='asn'):
    retSet = set()
    for ss in wn.synsets(term.strip(),pos=pos):
        for lemma in ss.lemmas():
            for anto in lemma.antonyms():
                retSet.add(anto.name().lower())
    retSet.discard(term)
    return retSet
def get_hyperyms(term,pos='asn'):
    retSet = set()
    for ss in wn.synsets(term.strip(),pos=pos):
        for hyper in ss.hypernyms() + ss.instance_hypernyms():
            for lemma in hyper.lemma_names():
                retSet.add(lemma.lower())
    retSet.discard(term)
    return retSet
def get_hyponyms(term,pos='asn'):
    retSet = set()
    for ss in wn.synsets(term.strip(),pos=pos):
        for rel in ss.hyponyms() + ss.instance_hyponyms():
            for lemma in rel.lemma_names():
                retSet.add(lemma.lower())
    retSet.discard(term)
    return retSet
def get_holonyms(term,pos='asn'):
    retSet = set()
    for ss in wn.synsets(term.strip(),pos=pos):
        for rel in ss.part_holonyms() + ss.member_holonyms() + ss.substance_holonyms():
            for lemma in rel.lemma_names():
                retSet.add(lemma.lower())
    retSet.discard(term)
    return retSet
def get_meronyms(term,pos='asn'):
    retSet = set()
    for ss in wn.synsets(term.strip(),pos=pos):
        for rel in ss.part_meronyms() + ss.member_meronyms() + ss.substance_meronyms():
            for lemma in rel.lemma_names():
                retSet.add(lemma.lower())
    retSet.discard(term)
    return retSet
def get_siblings(term,pos='asn'):
    retSet = set()
    for ss in wn.synsets(term.strip(),pos=pos):
        for hyper in ss.hypernyms():
            for hypo in hyper.hyponyms():
                for lemma in hypo.lemma_names():
                    retSet.add(lemma.lower())
    retSet.discard(term)
    return retSet
def get_derivationally_related_forms(term,pos='asn'):
    retSet = set()
    for ss in wn.synsets(term.strip(),pos=pos):
        for lemma in ss.lemmas():
            for rel in lemma.derivationally_related_forms():
                retSet.add(rel.name().lower())
    retSet.discard(term)
    return retSet
def get_pertainyms(term,pos='asn'):
    retSet = set()
    for ss in wn.synsets(term.strip(),pos=pos):
        for lemma in ss.lemmas():
            for rel in lemma.pertainyms():
                retSet.add(rel.name().lower())
    retSet.discard(term)
    return retSet

# all the words involved
word_set = set()

## matches:
# 1. (.*) all string within parenthesis; "\N" is the null value for mysql. # FIXME test only by now
rSpecial4name = re.compile(r"\s\(.*\)")
rSpecial4umls = re.compile(r"\(.*\)|[^a-zA-Z0-9-_ ]|- |- |\b-\b")
with open(input_file, 'rb') as csvfile:
    with open(output_file, 'w+') as of:
        reader = csv.reader(csvfile, delimiter=',', quotechar='"')
        writer = csv.writer(of,delimiter='\t',quotechar='"',lineterminator="\n")
        headline = ['word','word_norm','umls_syn','wn_syn','wn_ant','wn_hyper','wn_hypo','wn_holo','wn_mero','wn_sibling','derivation','pertain']
        writer.writerow(headline)
        stat_unigram = [0 for w in headline]
        stat_term = [0 for w in headline]
        stat_phrase = [0 for w in headline]  # word => [totalCnt, phraseCnt]
        for row in reader:
            if len(row) < 1: continue
            word = row[0]
            print("\n### word: %s" % word, file=sys.stderr)
            word_norm = rSpecial4name.sub('',word).strip().replace(' ','_')
            isValidTerm = False  # whether this is term is found in wordnet or umls
            # word_set.add(word_norm)
            cui = '' if len(row) < 2 else  row[1].strip()
            if len(cui) > 0: isValidTerm = True
            synUmls = [] if len(row) < 3 else  row[2].split(r'|') # it is '|' which is in database
            synUmlsSet = set()
            for s in synUmls:
                s2 = '_'.join(lemma(rSpecial4umls.sub('',s.replace(r'\N','')).lower().strip())) # \N is null in mysql
                if len(s2) == 0: continue
                synUmlsSet.add(s2)
            synUmlsSet.discard(word_norm)
            word_set |= synUmlsSet
            print( synUmlsSet, file=sys.stderr)
            synWnSet = get_synonyms(word_norm)
            word_set |= synWnSet
            if len(synWnSet) > 0: isValidTerm = True
            print(synWnSet, file=sys.stderr)
            if not isValidTerm:
                # if no synomym term in umls and wordnet, it is not considered to be a 'valid term'.
                continue

            antonyms = get_antonyms(word_norm)
            word_set |= antonyms
            print (antonyms, file=sys.stderr)
            hypernym = get_hyperyms(word_norm)
            word_set |= hypernym
            print(hypernym, file=sys.stderr)
            hyponym = get_hyponyms(word_norm)
            word_set |=hyponym
            print(hyponym, file=sys.stderr)
            holonym = get_holonyms(word_norm)
            word_set |= holonym
            print(holonym, file=sys.stderr)
            meronym = get_meronyms(word_norm)
            word_set |= meronym
            print(meronym, file=sys.stderr)
            siblings = get_siblings(word_norm)
            word_set |= siblings
            print(siblings, file=sys.stderr)
            derivation = get_derivationally_related_forms(word_norm)
            word_set |= derivation
            print(derivation, file=sys.stderr)
            pertain = get_pertainyms(word_norm)
            word_set |= pertain
            print(pertain, file=sys.stderr)

            word_set.add(word_norm)

            sep = ','
            row = [word,word_norm,sep.join(synUmlsSet),sep.join(synWnSet),sep.join(antonyms),sep.join(hypernym),\
                             sep.join(hyponym),sep.join(holonym),sep.join(meronym),sep.join(siblings),sep.join(derivation),sep.join(pertain)]
            writer.writerow(row)

            # get statistics info about evaluation data set
            for i,col in enumerate(row):
                if len(col.strip()) > 0:
                    stat_term[i] += 1
                rels = col.split(sep)
                for r in rels:
                    if len(r.strip()) == 0: continue
                    if '_' in r:
                        stat_phrase[i] += 1
                    else:
                        stat_unigram[i] += 1

        print("statistic info of count: all: %d, phrase %d" % (sum(stat_unigram) + sum(stat_phrase), sum(stat_phrase)))
        print('\t'.join(["Head"] + headline))
        print('\t'.join(["unigram"] + [str(i) for i in stat_unigram]))
        print('\t'.join(["Phrase"] + [str(i) for i in stat_phrase]))
        print('\t'.join(["Term"] + [str(i) for i in stat_term]))

        with open(wordset_file, 'w+') as wf:
            for w in word_set:
                wf.write("%s\n" % w)



