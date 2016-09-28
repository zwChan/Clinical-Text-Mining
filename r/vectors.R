org =read.table("C:\\Users\\Jason\\Desktop\\vectors.csv",header=TRUE,sep=',')
x=seq(1,nrow(org))
gram1=subset(org,org['n']==1)
gram2=subset(org,org['n']==2)
gram3=subset(org,org['n']==3)
gram4=subset(org,org['n']==4)
gram5=subset(org,org['n']==5)
gram6=subset(org,org['type']=="chv")
gram7=subset(org,org['type']=="umls")
gram8=subset(org,org['type']=="other")

n=1
matplot(seq(1,nrow(gram1)),log(gram1[,'tf']),type='l',pch=n,col=n, xlab="Index of ranked n-grams", ylab="log(term frequency)")
matpoints(seq(1,nrow(gram1))[seq(1,nrow(gram1),500)],log(gram1[,'tf'])[seq(1,nrow(gram1),500)],pch=n,col=n)

n=2
matplot(seq(1,nrow(gram2)),log(gram2[,'tf']),type='l',pch=n,col=rainbow(5,start=0.2), add=TRUE)
matpoints(seq(1,nrow(gram2))[seq(1,nrow(gram2),500)],log(gram2[,'tf'])[seq(1,nrow(gram2),500)],pch=n,col=n)

n=3
matplot(seq(1,nrow(gram3)),log(gram3[,'tf']),type='l',pch=n,col=rainbow(5,start=0.3), add=TRUE)
matpoints(seq(1,nrow(gram3))[seq(1,nrow(gram3),500)],log(gram3[,'tf'])[seq(1,nrow(gram3),500)],pch=n,col=n)

n=4
matplot(seq(1,nrow(gram4)),log(gram4[,'tf']),type='l',pch=n,col=rainbow(5,start=0.4), add=TRUE)
matpoints(seq(1,nrow(gram4))[seq(1,nrow(gram4),500)],log(gram4[,'tf'])[seq(1,nrow(gram4),500)],pch=n,col=n)

n=5
matplot(seq(1,nrow(gram5)),log(gram5[,'tf']),type='l',pch=n,col=rainbow(5,start=0.5), add=TRUE)
matpoints(seq(1,nrow(gram5))[seq(1,nrow(gram5),500)],log(gram5[,'tf'])[seq(1,nrow(gram5),500)],pch=n,col=n)

legend("topright",legend = c("1-gram","2-gram","3-gram","4-gram","5-gram"), col=1:n, pch=1:n) # optiona



n=6
matplot(seq(1,nrow(gram6)),log(gram6[,'tf']),type='l',pch=n,col=rainbow(n,start=0.1*n), xlab="Index of ranked n-grams", ylab="log(term frequency)")
matpoints(seq(1,nrow(gram6))[seq(1,nrow(gram6),500)],log(gram6[,'tf'])[seq(1,nrow(gram6),500)],pch=n,col=n)

n=7
matplot(seq(1,nrow(gram7)),log(gram7[,'tf']),type='l',pch=n,col=rainbow(n,start=0.1*n), add=TRUE)
matpoints(seq(1,nrow(gram7))[seq(1,nrow(gram7),500)],log(gram7[,'tf'])[seq(1,nrow(gram7),500)],pch=n,col=n)

n=8
matplot(seq(1,nrow(gram8)),log(gram8[,'tf']),type='l',pch=n,col=rainbow(n,start=0.1*n), add=TRUE)
matpoints(seq(1,nrow(gram8))[seq(1,nrow(gram8),500)],log(gram8[,'tf'])[seq(1,nrow(gram8),500)],pch=n,col=n)

legend("topright",legend = c("CHV terms","UMLS-CHV trems","other terms"), col=6:n, pch=6:n) # optiona

