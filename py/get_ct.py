__author__ = 'Jason'


import urllib2
from urllib2 import urlopen
from bs4 import BeautifulSoup
import re


def visible(element):
    if element.parent.name in ['style', 'script', '[document]', 'head', 'title']:
        return False
    # elif re.match("<!--.*-->", str(element)):
    #     return False
    return True

f = open("C:\\fsu\\ra\\data\\201612\\index_url.txt")
for line in f.readlines():
    if len(line)<10: continue
    (index, url) = line.split('\t',1)
    #url = "https://www.ncbi.nlm.nih.gov/pubmed/20482476"
    # print (url)
    try:
        html = urllib2.urlopen(url).read()
        soup = BeautifulSoup(html, 'html.parser')
        texts = soup.findAll(text=True)
        visible_texts = filter(visible, texts)
        text = filter(lambda x: len(x.strip())>5, visible_texts)
        text2 = " ".join(text)
        match = re.match(".*(\\bNCT\\d{5,15}\\b).*", text2, re.MULTILINE+re.DOTALL+re.UNICODE)
        if match is not None:
            ct = match.group(1)
            # print (text2.encode('utf-8'))
            print("%s\t%s" % (index, ct))
        #elif None != re.match(".*(\\bclinicaltrials\\.gov\\b).*", text2, re.MULTILINE+re.DOTALL+re.UNICODE+re.IGNORECASE):
        elif None != re.match(".*(\\bclinicaltrials\\b).*", text2, re.MULTILINE+re.DOTALL+re.UNICODE+re.IGNORECASE):
            print("%s\t%s" % (index, "clinicaltrials.gov"))
        else:
            print("%s\t%s" % (index, "None"))
    except:
        print("%s\t%s" % (index, "Error"))

