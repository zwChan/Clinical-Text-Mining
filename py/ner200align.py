__author__ = 'Jason'


f = open("""C:\\Users\\Jason\\Dropbox\\clinicalTrialPattern\\Evaluation\\random_200_sentences_cancer_studies-extend-non-major-term.txt""")
out = open("""C:\\Users\\Jason\\Dropbox\\clinicalTrialPattern\\Evaluation\\random_200_sentences_cancer_studies-extend-non-major-term-ret.txt""",'w+')

firstLine = True
for line in f.readlines():
    if len(line.strip())>10:
        tokens_org = line.strip().split('\t')
        tokens_org[0]=tokens_org[1]
        other_term = tokens_org[3].strip('\" ')
        if not firstLine:
            tokens_org[3]=""
        out.write('\t'.join(tokens_org) + '\n')
        if firstLine==False and len(other_term.strip())>1:
            for other in other_term.split(','):
                tokens_org[2]=other.strip()
                out.write('\t'.join(tokens_org) + '\n')
    firstLine = False

f.close()
out.close()
