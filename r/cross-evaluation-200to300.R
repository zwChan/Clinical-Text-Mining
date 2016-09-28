library('base')

# tf > 100, filter cluster < 3
data = read.table("C:\\fsu\\ra\\data\\cross-evaluation-tf100-200to300.txt",sep='\t')
rd_pc=25.5


data.avg=aggregate(data[,1:ncol(data)], list(data[,1]),mean)

# ev=read.table("C:\\fsu\\ra\\data\\evaluation.txt", sep = '\t')
ev=data.avg[order(data.avg$Group.1),]

write.table(data.avg[,2:ncol(data.avg)], "C:\\fsu\\ra\\data\\tmp.txt", sep='\t',row.names = FALSE,col.names = FALSE)
x = matrix(seq(5,200,5),ncol=1) * (100/100)

# random baseline data
y_rd_pc = rep(rd_pc,40)
dim(y_rd_pc)=c(40,1)
y_rd_rc = seq(5,200,5)*rd_pc/100
dim(y_rd_rc)=c(40,1)
y_rd_fs=(1+0.5^2)*(y_rd_pc*y_rd_rc)/((0.5^2*y_rd_pc+y_rd_rc))/100


#precision
startcol=11+40
y = t(ev[1:(nrow(ev)),startcol:(startcol+40-1)])
y = cbind(y,y_rd_pc)
matplot(x,y,type=c('l'),
        #pch=c(1,2,3),
        lwd=1,
        lty=1,
        #add=TRUE,
        col=gray.colors(nrow(ev),0.9,0),
        xlab="top-N percent", ylab="precision (%)")
y2 = t(ev[1:2,startcol:(startcol+40-1)])
y2 = cbind(y2,y_rd_pc)
matplot(x,y2,type=c('o'),
        pch=c(1,5,6),
        lwd=1,
        lty=1,
        lend=3,
        add=TRUE,
        col=rainbow(3,start=1))



legend("topright",legend = c("tf", "c-value", "random", "k=5", "k=300"), 
       col=c(rainbow(3,start=1),
             gray.colors(2,0.9,0)), 
       pch=c(1,5,6,16,16)) # optiona


#recall
startcol=11+00
y = t(ev[1:(nrow(ev)),startcol:(startcol+40-1)])
y = cbind(y,y_rd_rc)
#View(y)
matplot(x,y,type=c('l'),
        #pch=c(1,2,3),
        lwd=1,
        lty=1,
        #add=TRUE,
        col=gray.colors(nrow(ev),0.9,0),
        xlab="top-N percent", ylab="recall (%)")
y2 = t(ev[1:2,startcol:(startcol+40-1)])
y2 = cbind(y2,y_rd_rc)
matplot(x,y2,type=c('o'),
        pch=c(1,5,6),
        lwd=1,
        lty=1,
        lend=3,
        add=TRUE,
        col=rainbow(3,start=1))
legend("topleft",legend = c("tf", "c-value", "random", "k=5", "k=300"), 
       col=c(rainbow(3,start=1),
             gray.colors(2,0.9,0)), 
       pch=c(1,5,6,16,16)) # optiona


#f-score
startcol=11+80
y = t(ev[1:(nrow(ev)),startcol:(startcol+40-1)])
y = cbind(y,y_rd_fs)
#View(y)
matplot(x,y,type=c('l'),
        #pch=c(1,2,3),
        lwd=1,
        lty=1,
        #add=TRUE,
        col=gray.colors(nrow(ev),0.9,0),
        xlab="top-N percent", ylab="f-score")

y2 = t(ev[1:2,startcol:(startcol+40-1)])
y2 = cbind(y2,y_rd_fs)
matplot(x,y2,type=c('o'),
        pch=c(1,5,6),
        lwd=1,
        lty=1,
        lend=3,
        add=TRUE,
        col=rainbow(3,start=1))

legend("topright",legend = c("tf", "c-value","random", "k=5", "k=300"), 
       col=c(rainbow(3,start=1),
             gray.colors(3,0.9,0)), 
       pch=c(1,5,6,16,16)) # optiona
