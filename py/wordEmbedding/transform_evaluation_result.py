from __future__ import division,print_function
__author__ = 'Jason'

import sys
import re
import numpy as np
import matplotlib.pyplot as plt
import matplotlib.cm as cm
import pylab

'''
evalVocab isIntersectVacab=False, vocab number is 799072
Evaluation:	umls_syn	wn_syn	wn_ant	wn_hyper	wn_hypo	wn_holo	wn_mero	wn_sibling	derivation	pertain
cnt:	4934	32605	1366	47513	84282	7734	9968	639399	18480	288
cnt_term:	3195	8470	1042	11118	6492	3074	2139	10439	6941	240
cnt_avg:	1.54	3.85	1.31	4.27	12.98	2.52	4.66	61.25	2.66	1.20
hit_cnt:	1161	2844	290	1990	1499	510	419	6807	2248	130
hit_term:	1069	2427	282	1824	1185	478	349	4510	2080	128
hit_weight:	891.80	1134.35	228.50	868.68	368.07	293.00	157.58	1414.80	1156.77	117.58
hit_cnt_avg:	1.086	1.172	1.028	1.091	1.265	1.067	1.201	1.509	1.081	1.016
hit_cnt_pct:	23.5306	8.7226	21.2299	4.1883	1.7786	6.5943	4.2035	1.0646	12.1645	45.1389
hit_cnt_term_pct:	33.4585	28.6541	27.0633	16.4058	18.2532	15.5498	16.3160	43.2034	29.9669	53.3333
hit_weight_term_pct:	27.9124	13.3926	21.9290	7.8133	5.6695	9.5316	7.3671	13.5530	16.6657	48.9931


section	accuracy	correct	incorrect
capital-common-countries	99.60	504	2
capital-world	86.89	3931	593
currency	16.09	130	678
city-in-state	77.26	1906	561
family	85.50	395	67
airlines	47.62	20	22
total	78.17	6886	1923


'''
class RelationRet:
    colName = []
    rowName = []

    def __init__(self):
        self.value = []

    def __str__(self):
        ret = ""
        if self.value:
            ret += 'tag\ttask\ttopn\t%s\n' % ('\t'.join(self.colName))
            for val in self.value:
                ret += '%s\t%s\t%d\t%s\n' % (val[0],val[1],val[2],'\t'.join(['%0.2f'% x if type(x) == float else '%d'% x for x in val[3:]]))

            ret += '\n\n'
        return ret
def transpose(value):
    row_n = len(value)
    col_n = len(value[0])
    new_val = []
    for j in range(0,col_n):
        new_val.append([])
        for i in range(0,row_n):
            new_val[j].append(value[i][j])
    return new_val

def get_relation_data_file(retFile, startString):
    value = []
    colName = []
    rowName = []
    with open(retFile) as f:
        startRet = False
        for line in f.readlines():
            line = line.strip()
            # add row name line
            if startRet == False and line.startswith(startString):
                colName += line.split()[1:]
                startRet = True
                continue
            if startRet and len(line) > 0:
                cols = line.split()
                rowName.append(cols[0].strip(':'))
                value.append([float(x) for x in cols[1:]])
            else:
                # if startRet:
                #     value = np.array(rr.value)
                #     value
                startRet = False

    return (value,colName,rowName)

def get_relation_data(retFile,isRelation=False):
    files_name = retFile.split(',')
    rr = RelationRet()
    for f_name in files_name:
        tokens = f_name.split('#')
        fn = tokens[0]
        tag = tokens[0] if len(tokens)<2 else tokens[1]
        print(fn,file=sys.stderr)
        topn = re.match(r'.*top(\d+)$',fn)
        if topn == None:
            topn = 10
        else:
            topn = int(topn.group(1))
        topn = int(topn)
        if isRelation:
            (value,colname,rowname) = get_relation_data_file(fn,'Evaluation:')
            rowname,colname = colname,rowname
            value = transpose(value)
        else:
            (value,colname,rowname) = get_relation_data_file(fn,'section\t')
        rr.colName = colname
        rr.rowName = rowname
        for i,v in enumerate(value):
            rr.value.append([tag] + [rowname[i]] + [topn] + v)

    return rr


if len(sys.argv) < 2:
    print("Usage: [input_file.csv] [relation|analogy")
    exit(1)
print(sys.argv,file=sys.stderr)
np.set_printoptions(precision=2,linewidth=120,suppress=True)
input_file = sys.argv[1]
isRelation = sys.argv[2].lower()=='relation'
# wordset_file = sys.argv[3]
rr = get_relation_data(input_file, isRelation)
print(rr)

'''
Return a new array that its elements are unique and keep their order.
'''
def unique(arr):
    s = set()
    newarr = []
    for i in arr:
        if i not in s:
            newarr.append(i)
            s.add(i)
    return newarr

'''
data: 2-d list contains result data. the first three column are: method, task, topn; then is the result data colums
methods: list of methods. '*' for all
task: list of tasks. '*' for all
topn: list of top-n. '*' for all
col: index of the column that was retrieved
'''
def get_row(data,methods,task,topn,col):
    ret = []
    tag_list = []
    task_list = []
    topn_list = []
    for row in data:
        if (methods == '*' or row[0] in methods) and (task == '*' or row[1] in task) and (topn=='*' or row[2] in topn):
            tag_list.append(row[0])
            task_list.append(row[1])
            topn_list.append(row[2])
            if col == '*':
                ret.append(row[3:])
            else:
                ret.append(row[col])
    return (ret,unique(tag_list),unique(task_list),unique(topn_list))

'''
colors: https://matplotlib.org/users/colormaps.html
https://matplotlib.org/examples/color/named_colors.html
https://matplotlib.org/examples/pylab_examples/hatch_demo.html
'''


PATTERN = ('///', '.', '*', '\\\\',  'o')
#colors = cm.pastel1
'''
method-analogy
'''
topN = [1,5,20,100]
# tag = 'word2vec'
# task = ["capital-common-countries", "capital-world", "currency", "city-in-state", "family","airlines"]

def fig_method_topn(rr,method,colName,topN,xlabel,ylabel):
    # create plot
    f = plt.figure()
    # fig, ax = plt.subplots()
    bar_width = (1-0.2)/len(topN)
    opacity = 0.8
    colors = cm.jet(np.linspace(0, 1, len(topN)))
    if isRelation:
        plt.ylim([0,90])
    else:
        plt.ylim([0,110])
    for i,topn in enumerate(topN):
        col = rr.colName.index(colName) + 3
        row,method_list,task_list,topn_list = get_row(rr.value, [method], '*', [topn], col)
        n_groups = len(task_list)
        index = np.arange(n_groups)
        plt.bar(index + i*bar_width, row, bar_width,
                         alpha=opacity,
                         # color='lightgrey',
                         color = colors[i],
                         hatch=PATTERN[i],
                         label='top %d' % topn)

    plt.xlabel(xlabel)
    plt.ylabel(ylabel)
    # plt.title('Analogy tasks performance of Word2vec')
    for i in range(0,len(task_list)):
        if len(task_list[i]) >= 15:
            task_list[i] = str(task_list[i][:14]) + "..."
    plt.xticks(index + 0.1, task_list, rotation=-30,ha='left')
    plt.legend(loc='best',ncol=len(task_list), fontsize='small')

    plt.tight_layout()
    # plt.show()
    if isRelation:
        savename = "%s-relation.jpg" % method
    else:
        savename = "%s-analogy.jpg" % method

    f.savefig(savename, bbox_inches='tight', dpi=200)
    plt.close()
    print("save image %s" % savename)


def fig_compare_methods(rr,methods,colName,topN,xlabel,ylabel):
    # create plot
    f = plt.figure()
    # fig, ax = plt.subplots()
    bar_width = (1-0.2)/len(methods)
    opacity = 0.8
    colors = cm.jet(np.linspace(0, 1, len(methods)))
    if isRelation:
        plt.ylim([0,70])
    else:
        plt.ylim([0,110])
    for i,m in enumerate(methods):
        col = rr.colName.index(colName[0]) + 3
        row,method_list,task_list,topn_list = get_row(rr.value, [m], '*', topN, col)
        n_groups = len(task_list)
        index = np.arange(n_groups)
        plt.bar(index + i*bar_width, row, bar_width,
                         alpha=opacity,
                         # color='lightgrey',
                         color=colors[i],
                         hatch=PATTERN[i],
                         label='%s' % methods[i])

    plt.xlabel(xlabel)
    plt.ylabel(ylabel)
    # plt.title('Analogy tasks performance of Word2vec')
    for i in range(0,len(task_list)):
        if len(task_list[i]) >= 15:
            task_list[i] = str(task_list[i][:14]) + "..."
    plt.xticks(index + 0.1, task_list, rotation=-30,ha='left')
    plt.legend(loc='best',ncol=len(task_list), fontsize='small')

    plt.tight_layout()
    # plt.show()
    if isRelation:
        savename = "%s-%s-top%d-relation.jpg" % (''.join([x[0] for x in methods]),colName[0],topN[0])
    else:
        savename = "%s-%s-top%d-analogy.jpg" % (''.join([x[0] for x in methods]),colName[0],topN[0])

    f.savefig(savename, bbox_inches='tight', dpi=200)
    plt.close()
    print("save image %s" % savename)


#################################################
value,methods,tasks,topns = get_row(rr.value,'*','*','*','*')
methods = methods
tasks = tasks
topns = sorted(topns)

#### method detial vs topn performance
for m in methods:
    if isRelation:
        fig_method_topn(rr,m,'hit_cnt_term_pct',topns,'Semantic relation tasks','Retrieved terms ratio (%)')
    else:
        fig_method_topn(rr,m,'accuracy',topns,'Analogy tasks','Accuracy (%)')

#### method compaire on unigram
for topn in topN:
    if isRelation:
        fig_compare_methods(rr,['word2vec','deps-word2vec','glove','phrase4word','norm'],['hit_cnt_term_pct'],[topn],'Semantic relation tasks','Retrieved ratio (%)')
        fig_compare_methods(rr,['word2vec','deps-word2vec','glove','phrase4word','norm'],['hit_weight_term_pct'],[topn],'Semantic relation tasks','weighted Retrieved ratio (%)')
    else:
        fig_compare_methods(rr,['word2vec','deps-word2vec','glove','phrase4word','norm'],['accuracy'],[topn],'Analogy tasks','Accuracy (%)')

#### method compaire on phrases
for topn in topN:
    if isRelation:
        fig_compare_methods(rr,['linear-estimate','word2phrase','ner'],['hit_cnt_term_pct'],[topn],'Semantic relation tasks','Retrieved ratio (%)')
        fig_compare_methods(rr,['linear-estimate','word2phrase','ner'],['hit_weight_term_pct'],[topn],'Semantic relation tasks','weighted Retrieved ratio (%)')
    else:
        fig_compare_methods(rr,['linear-estimate','word2phrase','ner'],['accuracy'],[topn],'Analogy tasks','Accuracy (%)')
