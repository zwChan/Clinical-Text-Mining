library('base')


ev=read.table("C:\\fsu\\ra\\data\\evaluation.txt", sep = '\t')

x = matrix(seq(5,200,5),ncol=1) * (1000/100)
y = t(ev[4:30,49:88])
matplot(x,y,type=c('l'),
        #pch=c(1,2,3),
        lwd=1,
        lty=1,
        col=gray.colors(nrow(x),0.7,0),
        xlab="top-N", ylab="precision (%)")


y = t(ev[2:3,49:88])
matplot(x,y,type=c('o'),
        pch=c(1,5),
        lwd=1,
        lty=1,
        lend=3,
        add=TRUE,
        col=rainbow(2,start=1),
        xlab="top-N", ylab="precision (%)")



legend("topright",legend = c("tf", "c-value", "k=5", "k=300"), 
       col=c(rainbow(2,start=1),
       gray.colors(2,0.7,0)), 
       pch=c(1,5,16,16)) # optiona




#fscore
y = t(ev[4:30,89:128])
matplot(x,y,type=c('l'),
        #pch=c(1,2,3),
        lwd=1,
        lty=1,
        col=gray.colors(nrow(x),0.7,0),
        xlab="top-N", ylab="fscore")


y = t(ev[2:3,89:128])
matplot(x,y,type=c('o'),
        pch=c(1,5),
        lwd=1,
        lty=1,
        lend=3,
        add=TRUE,
        col=rainbow(2,start=1),
        xlab="top-N", ylab="fscore")



legend("topleft",legend = c("tf", "c-value", "k=5", "k=300"), 
       col=c(rainbow(2,start=1),
             gray.colors(2,0.7,0)), 
       pch=c(1,5,16,16)) # optiona
