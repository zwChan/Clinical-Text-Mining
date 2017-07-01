from __future__ import division,print_function
__author__ = 'Jason'
import sys
import random
import csv
import logging
import  gensim
from gensim import utils, matutils
# logger = logging.getLogger(__name__)

class EvaluateAnalogy:
    def __init__(self,sections,topn=10):
        if len(sections) == 0:
            print("No term found.")
            return
        self.sections = sections

    def __str__(self):
        ret = "section\taccuracy\tcorrect\tincorrect\n"
        for section in self.sections:
            if len(section['correct'])+len(section['incorrect']) > 0:
                ret += "%s\t%0.2f\t%d\t%d\n" \
                       % (section['section'],100.0*len(section['correct'])/(len(section['correct'])+len(section['incorrect'])), len(section['correct']), len(section['incorrect']))
        correct = sum(len(s['correct']) for s in analogyList)
        incorrect = sum(len(s['incorrect']) for s in analogyList)
        ret += "%s\t%0.2f\t%d\t%d\n" \
            % ('total',100.0*correct/(correct+incorrect), correct, incorrect)
        return ret

class EvaluateRelation:
    """
    ['term name','term_norm', 'umls_syn','wn_syn','wn_ant','wn_hyper','wn_hypo','wn_holo','wn_mero','wn_sibling']
    """
    def __init__(self,termList, topn=10):
        if len(termList) == 0:
            print("No term found.")
            self.termList = []
            return
        self.termList = termList
        self.topn = topn
        self.termNum = len(termList)
        self.relName = self.termList[0].relName
        self.NameIndex = self.termList[0].NameIndex
        self.NormIndex = self.termList[0].NormIndex
        self.cnt = self.empty_ret() # occurring count of the relation words
        self.hit_cnt = self.empty_ret() #total of hit relation words
        self.cnt_term = self.empty_ret() # number of terms that has this relation word
        self.hit_term = self.empty_ret() # number of terms that is hit this relation word
        self.hit_cnt_weighted = self.empty_ret() # weighted hit number of relation word. weight = 1 / min(number of relation word, topn)

        # self.evaluate()

    def empty_ret(self):
        ret = []
        for i in range(0,len(self.relName)):
            ret.append(0)
        return ret

    def evaluate(self):
        if len(self.termList) == 0: return
        for term in self.termList:
            for i,rels in enumerate(term.relValue):
                if i <= term.NormIndex: continue  # skip term name and term norm
                # if len(term.relValue) > len(term.relHit):
                if len(rels) > 0:
                    self.cnt_term[i] += 1
                    self.hit_cnt_weighted[i] += 1.0*len(term.relHit[i])/min([len(rels),self.topn])
                self.cnt[i] += len(rels)
                if len(term.relHit[i]) > 0:
                    self.hit_term[i] += 1
                self.hit_cnt[i] += len(term.relHit[i])

    def __str__(self):
        if len(self.termList) == 0: return ""
        ret = 'Evaluation:\t%s\n' % ('\t'.join([str(i) for i in self.relName[self.NormIndex+1:]]))
        ret += "cnt:\t%s\n" % ('\t'.join([str(i) for i in self.cnt[self.NormIndex+1:]]))
        ret += "cnt_term:\t%s\n" % ('\t'.join([str(i) for i in self.cnt_term[self.NormIndex+1:]]))
        ret += "cnt_avg:\t%s\n" % ('\t'.join(['%0.2f' % (z[0]/z[1] if z[1]!=0 else 0) for z in zip(self.cnt,self.cnt_term)[self.NormIndex+1:]]))
        ret += "hit_cnt:\t%s\n" % ('\t'.join([str(i) for i in self.hit_cnt[self.NormIndex+1:]]))
        ret += "hit_term:\t%s\n" % ('\t'.join([str(i) for i in self.hit_term[self.NormIndex+1:]]))
        ret += "hit_weight:\t%s\n" % ('\t'.join(["%0.2f" % (i) for i in self.hit_cnt_weighted[self.NormIndex+1:]]))
        ret += "hit_cnt_avg:\t%s\n" % ('\t'.join(['%0.3f' % (z[0]/z[1] if z[1]!=0 else 0) for z in zip(self.hit_cnt,self.hit_term)[self.NormIndex+1:]]))
        ret += "hit_cnt_pct:\t%s\n" % ('\t'.join(['%0.4f' % (100*z[0]/z[1] if z[1]!=0 else 0) for z in zip(self.hit_cnt,self.cnt)[self.NormIndex+1:]]))
        ret += "hit_cnt_term_pct:\t%s\n" % ('\t'.join(['%0.4f' % (100*z[0]/z[1] if z[1]!=0 else 0) for z in zip(self.hit_term,self.cnt_term)[self.NormIndex+1:]]))
        ret += "hit_weight_term_pct:\t%s\n" % ('\t'.join(['%0.4f' % (100*z[0]/z[1] if z[1]!=0 else 0) for z in zip(self.hit_cnt_weighted,self.cnt_term)[self.NormIndex+1:]]))
        ret += "\n"
        return ret

    def PrintHitList(self):
        if len(self.termList) == 0: return
        print('\t'.join(self.relName))
        for t in self.termList:
            print(t)

class Term:
    """
    ['term name','term_norm', 'umls_syn','wn_syn','wn_ant','wn_hyper','wn_hypo','wn_holo','wn_mero','wn_sibling']
    """
    relName = []
    NameIndex = 0
    NormIndex = 1

    def __init__(self, term=''):
        self.name = term # name of the term, e.g. Love (N)
        self.relValue = [] # relation word of the term. e.g. synonym, antonym, ...
        self.relHit = []   # the relation word is hit in the nearest neighbors.
        self.similar = []  # similar terms retrieved from embedding vectors
    def __str__(self, simple=True):
        ret = ''
        if simple:
            ret += "%s\t" % ('\t'.join(['|'.join(t) for t in self.relValue[0:self.NormIndex+1]]))
            ret += "%s" % ('\t'.join([ '|'.join(t) for t in self.relHit[self.NormIndex+1:]]))
        else:
            ret += 'name:\t%s, norm:%s\n' % (self.name,self.relValue[self.NormIndex][0])
            ret += 'similar:\t'
            for sim in self.similar:
                ret += "(%s,%0.2f), " % sim
            ret += '\n'
            for i,title in enumerate(self.relName):
                if i < self.NormIndex: continue
                ret += "%s:\t%s\n" % (title,' '.join(self.relHit[i]))
            ret += '\n'
        return ret
    def similarMap(self):
        simMap = {}
        for kv in self.similar:
            simMap[kv[0]] = kv[1]
        return simMap

def phrase2word(w2v,phrase,strict_match=True):
    global evalVocab
    term_norm_estimate = []
    miss = False
    for t in phrase.split('_'):
        t2 = t.strip()
        if len(t2) > 0:
            if t2 in wv.vocab and t2 in evalVocab:
                term_norm_estimate.append(t2)
            else:
                miss |= True
    return term_norm_estimate if strict_match == False or miss == False else []

def estimate_phrase(w2v,phrase,most_similar_f,topn=1):
    rel_estimate = []
    rel_words = phrase2word(w2v,phrase)
    try:
        rel_estimate_ret = most_similar_f(w2v,rel_words,topn=topn)
        rel_estimate = [kv[0] for kv in rel_estimate_ret]
    except:
        print("Fail to estimate phrase: %s" % (phrase), file=sys.stderr)
    return rel_estimate


def safe_phrase(w2v,phrase,most_similar_f):
    global evalVocab
    if phrase in w2v.vocab:
        return [phrase]
    elif phrase in evalVocab and '_' in phrase:
        ret = estimate_phrase(w2v,phrase,most_similar_f,topn=1)
        print("phrase (%s) estimate to (%s)" % (phrase, ret),file=sys.stderr)
        return ret
    else:
        return []

def term_similar(w2v, term,  most_similar_f, topn,restrict_vocab=30000):
    term_norm = term.relValue[term.NormIndex][0] if len(term.relValue[term.NormIndex]) > 0 else ""
    try:
        if len(term_norm) > 0:
            term.similar = most_similar_f(w2v,term_norm,topn=topn,restrict_vocab=restrict_vocab)
    except KeyError as e:
        if '_' in term_norm:
            term_norm_estimate = safe_phrase(w2v,term_norm,most_similar_f)
            try:
                if len(term_norm_estimate) > 0:
                    term.similar = most_similar_f(w2v,term_norm_estimate,topn=topn,restrict_vocab=restrict_vocab)
                    print("phrase %s estimate term is %s\nsimilar: %s" % (term_norm, term_norm_estimate, term.similar), file=sys.stderr)
            except KeyError as e2:
                print("No one of the words in phrase: %s in" % ('_'.join(term_norm)), file=sys.stderr)
            print("**INFO** term %s not found, use estimate %s, similar terms %s" % (term_norm, term_norm_estimate, term.similar), file=sys.stderr)
        else:
            print("No such word: %s" % (term_norm), file=sys.stderr)
    simMap = term.similarMap()
    if len(simMap) == 0: return  False

    for rels in term.relValue:
        hitTerms = []
        for rel in rels:
            if rel in wv.vocab:
                if rel in simMap:
                    hitTerms.append(rel)
            elif '_' in rel:  # use the average vector of words in phrase to estimate the phrase
                rel_estimate = safe_phrase(w2v,rel,most_similar_f)
                estimate_hit = []
                for estimate in rel_estimate:
                    if estimate in simMap:
                        print("rel %s estimate %s, hit" % (rel,rel_estimate), file=sys.stderr)
                        estimate_hit.append(estimate)
                        hitTerms.append(rel)
                        break  # need only hit one target term.
                print("**INFO** in term %s, target phrase %s, estimate word %s, hit %s" % (term.name, rel, rel_estimate, estimate_hit), file=sys.stderr)
        term.relHit.append(hitTerms)
    return True

'''
    sample: use part of the evaluation data, for test only
'''
def accuracy_rel(w2v, csvfile,most_similar_f, topn=10, restrict_vocab=30000,case_insensitive=True, usePhrase=True,sample=0):
    global evalVocab
    termList = []
    with open(csvfile, 'rb') as csvfile:
        csvreader = csv.reader(csvfile, delimiter='\t', quotechar='"')
        first = True
        for row in csvreader:
            if len(row) < 2: continue
            if first:
                first = False
                Term.relName += row
                continue

            if sample > 0 and random.random() > sample: continue  # sample question, for test only
            term = Term(row[0])
            for i,rel in enumerate(row):
                term.relValue.append([])
                # if len(rel)==0: continue
                if i == Term.NameIndex:  # add the name of the term
                    term.relValue[i].append(rel)
                    continue
                rel_term = rel.split(',')
                for t in rel_term:
                    if len(t.strip()) == 0: continue
                    if t.lower() not in evalVocab:
                        if i == Term.NormIndex :print("%s in term(%s) is ignored record for term caused by not-in-evalVocab " % (t, term.name), file=sys.stderr)
                        continue
                    if usePhrase == False and "_" in t:
                        if i == Term.NormIndex :print("%s in term(%s) is ignored record for term caused by phrase disable " % (t, term.name), file=sys.stderr)
                        continue
                    term.relValue[i].append(t)
            ret = term_similar(w2v,term,most_similar_f,topn,restrict_vocab)
            if ret:
                termList.append(term)
            else:
                print("term(%s) found no similar term, ignored " % (term.name), file=sys.stderr)
    return termList

def accuracy_analogy(wv, questions, most_similar, topn=10, case_insensitive=True,usePhrase=True, sample=0):
    """
    Compute accuracy of the model. `questions` is a filename where lines are
    4-tuples of words, split into sections by ": SECTION NAME" lines.
    See questions-words.txt in https://storage.googleapis.com/google-code-archive-source/v2/code.google.com/word2vec/source-archive.zip for an example.

    The accuracy is reported (=printed to log and returned as a list) for each
    section separately, plus there's one aggregate summary at the end.

    Use `restrict_vocab` to ignore all questions containing a word not in the first `restrict_vocab`
    words (default 30,000). This may be meaningful if you've sorted the vocabulary by descending frequency.
    In case `case_insensitive` is True, the first `restrict_vocab` words are taken first, and then
    case normalization is performed.

    Use `case_insensitive` to convert all words in questions and vocab to their uppercase form before
    evaluating the accuracy (default True). Useful in case of case-mismatch between training tokens
    and question words. In case of multiple case variants of a single word, the vector for the first
    occurrence (also the most frequent if vocabulary is sorted) is taken.

    sample: use part of the evaluation data, for test only
    This method corresponds to the `compute-accuracy` script of the original C word2vec.

    """
    global evalVocab
    self=wv
    ok_vocab = self.vocab
    ok_vocab = dict((w.lower(), v) for w, v in ok_vocab.items()) if case_insensitive else ok_vocab
    sections, section = [], None
    for line_no, line in enumerate(utils.smart_open(questions)):
        # TODO: use level3 BLAS (=evaluate multiple questions at once), for speed
        line = utils.to_unicode(line).lower()
        if line.startswith(': '):
            # a new section starts => store the old section
            if section:
                sections.append(section)
                self.log_accuracy(section)
                print("", file=sys.stderr)
            section = {'section': line.lstrip(': ').strip(), 'correct': [], 'incorrect': []}
        else:
            if usePhrase == False and "_" in line: continue
            if sample > 0 and random.random() > sample: continue  # sample question, for test only
            if not section:
                raise ValueError("missing section header before line #%i in %s" % (line_no, questions))
            try:
                if case_insensitive:
                    a, b, c, expected = [word.lower() for word in line.split()]
                else:
                    a, b, c, expected = [word for word in line.split()]
            except:
                print("skipping invalid line in %s, %s" % (section['section'], line.strip()), file=sys.stderr)
                continue

            original_vocab = self.vocab
            self.vocab = ok_vocab
            a2 = safe_phrase(wv,a,most_similar)
            b2 = safe_phrase(wv,b,most_similar)
            c2 = safe_phrase(wv,c,most_similar)
            expected2 = safe_phrase(wv,expected,most_similar)
            if len(a2)==0 or len(b2)==0 or len(c2)==0 or len(expected2)==0:
                print("skipping line in %s with OOV words: %s" % (section['section'], line.strip()), file=sys.stderr)
                print(a2,b2,c2,expected2,file=sys.stderr)
                continue
            #print("%s found words: %s\n" % (section['section'], line.strip()), file=sys.stderr)


            ignore = set(a2+b2+c2)  # input words to be ignored
            predicted = None
            # find the most likely prediction, ignoring OOV words and input words
            sims = most_similar(self, positive=b2+c2, negative=a2, topn=topn, restrict_vocab=None)
            self.vocab = original_vocab
            for word, dist in sims:
                predicted = word.lower() if case_insensitive else word
                if predicted in ok_vocab and predicted not in ignore:
                    if predicted in expected2:
                        break  # found.
            if predicted in expected2:
                section['correct'].append((a, b, c, expected))
            else:
                section['incorrect'].append((a, b, c, expected))
            print("\r%s: %0.2f%% correct %d, incorrect %d" %\
                  (section['section'],100.0*len(section['correct'])/(len(section['correct'])+len(section['incorrect'])), len(section['correct']), len(section['incorrect'])),\
                  end='', file=sys.stderr)

    if section:
        # store the last section, too
        sections.append(section)
        self.log_accuracy(section)

    # total = {
    #     'section': 'total',
    #     'correct': sum((s['correct'] for s in sections), []),
    #     'incorrect': sum((s['incorrect'] for s in sections), []),
    # }
    # self.log_accuracy(total)
    # sections.append(total)
    return sections

def get_evaluation_vocab(vocFile,otherVocab,isIntersectVacab=False):
    # construct the vocabulary for evaluation
    evalVocab = set()
    with open(vocFile) as f:
        for line in f.readlines():
            tokens = line.split()
            if len(tokens)>0 and len(tokens[0]) > 0:
                evalVocab.add(tokens[0].lower())

    for vf in otherVocab.split(','):
        if len(vf.strip()) == 0: continue
        vocab = set()
        with open(vf) as f:
            for line in f.readlines():
                tokens = line.split()
                if len(tokens)>0 and len(tokens[0]) > 0:
                    vocab.add(tokens[0].lower())
        if isIntersectVacab:
            evalVocab &= vocab
        else:
            evalVocab |= vocab
    return evalVocab

# --------------------------------------------------------------------------------
if len(sys.argv) < 5:
    print("Usage: [model-file] [vocab-file] [analogy-file] [relation-file] [top-n] [usePhrase(True|False)] [otherVocab(from the model to compare] [union|intersection] [sample(test)]",file=sys.stderr)
    exit(1)
print(sys.argv)
model = sys.argv[1]
# model = r'C:\fsu\class\thesis\token.txt.bin'
vocFile=sys.argv[2]
# vocFile = r'C:\fsu\class\thesis\token.txt.voc'
analogyfile = sys.argv[3]
# qfile = r'C:\fsu\ra\data\201706\synonym_ret.csv'
relfile = sys.argv[4]
topn = 10 if len(sys.argv) < 6 else int(sys.argv[5])
usePhrase = True if len(sys.argv) < 7 else sys.argv[6].strip().lower()!='false'  # True unless specify false
otherVocab = "" if len(sys.argv) < 8 else sys.argv[7].strip()
isIntersectVacab = False if len(sys.argv) < 9 else sys.argv[8].strip().lower()=="intersection"
sample = 0 if len(sys.argv) < 10 else float(sys.argv[9])

isBin = model.strip().endswith('bin')

evalVocab = get_evaluation_vocab(vocFile,otherVocab,isIntersectVacab)
print("evalVocab isIntersectVacab=%s, vocab number is %d" % (str(isIntersectVacab), len(evalVocab)))
wv = gensim.models.KeyedVectors.load_word2vec_format(model,fvocab=vocFile,binary=isBin)
termList = accuracy_rel(wv,relfile,gensim.models.KeyedVectors.most_similar,topn=topn,usePhrase=usePhrase,sample=sample)
evaluation_rel = EvaluateRelation(termList,topn=topn)
# print("### result start: ###")
# evaluation.PrintHitList()
# print("#### evaluation result ###")
evaluation_rel.evaluate()
print(evaluation_rel)

analogyList = accuracy_analogy(wv,analogyfile,gensim.models.KeyedVectors.most_similar,topn=topn, usePhrase=usePhrase,sample=sample)
evaluation_analogy = EvaluateAnalogy(analogyList,topn=topn)
print(evaluation_analogy)




