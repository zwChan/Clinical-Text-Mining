__author__ = 'Jason'


import csv
import sys,os,re


with open(r'C:\fsu\ra\data\201708\Copy of Botanical_with_dsld_cat_termlist.csv', 'w+') as output:
    with open(r'C:\fsu\ra\data\201708\Copy of Botanical_with_dsld_cat.csv', 'rb') as csvfile:
        spamreader = csv.reader(csvfile, delimiter=',', quotechar='"')
        head = True
        aui = 0
        for row in spamreader:
            '''column: id, name, Scientific Name, category_DSLD'''
            if head:
                head = False
                continue
            terms  = str(row[2])
            terms = re.sub(r'\.\s*Family:',', ',terms)
            terms = terms.replace('/',', ')
            terms = terms.replace(';',', ')
            terms = terms.replace('synonyms','')
            terms = terms.replace('synonym','')

            res_list = []
            terms_list = terms.split(', ')
            for term in terms_list:
                term = term.strip()
                term = term.strip('.,?!"\'')
                # extract (*)
                match = re.match(r'(.+?)\((.+?)\)(.*?)',term)
                if match == None:
                    print(term)
                    res_list.append(term)
                else:
                    res_list.append(match.group(1).strip() + match.group(3).strip())
                    res_list.append(match.group(2).strip())
                    print(term, match.group(1)+match.group(3), match.group(2))
            # print('\t'.join(res_list))

            cui = row[0]
            sab = 'unknown'
            for term in res_list:
                aui += 1
                preStr = term
                output.write('\t'.join([cui,str(aui),sab,term,preStr]) + '\n')


