
#data =read.table("C:\\fsu\\ra\\data\\rank-review-ranking.csv",header=TRUE,sep=',')
data =read.table("C:\\fsu\\ra\\data\\rank-review-ranking-30sample.csv",header=TRUE,sep=',')

ds=data[order(-data[,"kTfAvg"],data[,"cost"]),]
ds2 = cbind(ds,seq(1,nrow(ds),1)/nrow(ds))
chvCnt = sum(ds[,'type']=='chv')

recall = rep(0,nrow(ds))
for (i in seq(1,nrow(ds2),1)) {
  recall[i]=sum(ds[1:i,'type']=='chv')/chvCnt
}
precision = rep(0,nrow(ds))
for (i in seq(1,nrow(ds2),1)) {
  precision[i]=sum(ds[1:i,'type']=='chv')/i
}
fscore = rep(0,nrow(ds))
for (i in seq(1,nrow(ds2),1)) {
  fscore[i]=(1+0.5^2)*(precision[i]*recall[i]/(0.5^2*precision[i]+recall[i]))
}

x = seq(1,nrow(ds),1)*100/chvCnt
#precision
y = cbind(recall,precision,fscore)
matplot(x,y,type=c('l'),
        pch=c(1,5,6),
        lwd=1,
        lty=1,
        #add=TRUE,
        col=rainbow(3,start=1),
        xlab="top-N percent", ylab="recall/precision/F-score")

pp=seq(1,nrow(ds),1) %% 200==0
matpoints(x[pp], y[pp,], type = "p", lty = 1, lwd = 1, pch = c(1,5,6),
          col = rainbow(3,start=1))

legend("topright",legend = c("recall", "precision", "F-score"), 
       col=rainbow(3,start=1), 
       pch=c(1,5,6)) # optiona




