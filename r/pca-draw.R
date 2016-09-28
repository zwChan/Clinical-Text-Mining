library(rgl)

ngramspca=read.table("C:\\fsu\\ra\\data\\pca.txt")
plot(ngramspca[,1:3])
plot3d(ngramspca[,1:3])
apply(ngramspca[,1:10], 2, mean)
apply(ngramspca[,1:10], 2, sd)


ngramall=read.table("C:\\fsu\\ra\\data\\ngram_vectors_all_0227.txt")
tmp=subset(ngramall,ngramall[,6]>0.3)
#tmp=ngramall
tmp[,6]=0
p=prcomp(tmp,scale. = FALSE)
plot(p)
plot3d(p$x[,1:3])
plot(p$x[,1:3])
apply(p$x[,1:10], 2, mean)
apply(p$x[,1:10], 2, sd)
