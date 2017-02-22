# Read values from tab-delimited autos.dat 
autos_data <- read.table("C:/fsu/ra/UmlsTagger/r/data/human_review.txt", header=F, sep="\t")
name <- c("1-grams",	"2-grams",	"3-grams",	"4-grams",	"5-grams")

# Graph autos with adjacent bars using rainbow colors
bp <- barplot(as.matrix(autos_data),
        names=name,
        x.intersp=1,
        ylim=c(0,100),
        density =   c(30,40,50), 
        angle =  c(30,60,120),
        beside=TRUE, col=rainbow(3))

text(bp,as.matrix(autos_data),as.matrix(autos_data),cex=1,pos=3)

# Place the legend at the top-left corner with no frame  
# using rainbow colors
legend("top", c("Should not be added","Could possibly be added","Should be added"),
       cex=1.0, 
       density =   c(30,40,50), 
       angle =  c(30,60,120),
       bty="n", fill=rainbow(3));




# 
# 
# slices <- c(3,1,4,2) 
# names <- c( "¼×", "ÒÒ", "±û", "¶¡") 
# 
# #png("r-graph-sample.png") 
# 
# barplot(beside=TRUE, 
#         slices, #×ÝÖáÈ¡Öµ 
#         names.arg=names, #±ß¿òÃû×Ö 
#         border="black",  #±ß¿òÑÕÉ« 
#         col=c("purple","green3","blue","red"), #¿òÄÚÏßÌõÑÕÉ«
#         density =   c(7.5,12.5,17.5,22.5), #¿òÄÚÏßÌõÃÜ¶È
#         angle =  c(45,60,120,135),  #¿òÄÚÏßÌõÇãÐ±½Ç¶È
#         width = c(4,2.2,2.2,3),  #±ß¿ò¿í¶È 
#         space = c(1.5,0.5,0.5,1), #±ß¿ò¼ä¾à 
#         ylim=c(0,5),  #×ÝÖáÈ¡Öµ·¶Î§ 
# )  
# 
# title(xlab="ºá") #ºáÖáÃû×Ö 
# title(ylab="Êú") #×ÝÖáÃû×Ö 
# 
# lbls <- round(slices/sum(slices)*100) 
# lbls <- paste(lbls,"%",sep="") # ad % to labels 
# lbls <- paste(names, lbls) # add percents to labels 
# 
# #Í¼Àý 
# legend("topright",lbls, 
#        fill=c("purple","green3","blue","red"), 
#        density =   c(7.5,12.5,17.5,22.5), 
#        angle =  c(45,60,120,135),   
# ) 

