#########################################################
### A) Installing and loading required packages
#########################################################

if (!require("gplots")) {
  install.packages("gplots", dependencies = TRUE)
  library(gplots)
}
if (!require("RColorBrewer")) {
  install.packages("RColorBrewer", dependencies = TRUE)
  library(RColorBrewer)
}
if (!require("d3heatmap")) {
  install.packages("d3heatmap", dependencies = TRUE)
  library(d3heatmap)
}



df <- read.csv("C:\\fsu\\ra\\UmlsTagger\\r\\data\\cui-duration-freq.csv", sep=",",colClasses = "character")
#df <- read.csv("C:\\fsu\\ra\\data\\201601\\split_criteria\\cui-duration-freq.csv", sep=",",colClasses = "character")



cuis <- unique(df[,"cui"])  # already sort by the sql
durs <- sort(unique(df[,"month"]))


topN = 50
if (length(cuis)<topN)topN=length(cuis)

mat <- matrix(data=rep(0,topN*length(durs)),nrow=topN,ncol=length(durs))

rownames(mat) <- cuis[1:topN]
colnames(mat) <- as.character(sort(as.integer(durs)))

cuiStr = rep("",topN) #cuis[1:topN]  # for cui string
maxRow = 0
for (r in 1:nrow(df)) {
  cui = df[[r,"cui"]]
  if (cui %in%  cuis[1:topN]) {
    dur = as.character(df[r,"month"])
    mat[cui,dur] = as.integer(df[r,"num"])
    if (nchar(df[r,"cui_str"])>nchar(cuiStr[match(cui,cuis)])) {
      cuiStr[match(cui,cuis)] = sprintf("%s(%s)",df[r,"cui_str"],df[r,"sty"])
    }
    print(c(cui,dur,mat[cui,dur]))
    maxRow = r
  }
}

print(mat)

# only use the column that contain non-zero value
print(dim(mat))
print(colSums(mat))
colFilter = colSums(mat)!=0
mat <- mat[,colFilter]
mat <- matrix(mat,nrow=topN,ncol=sum(colFilter))
print(dim(mat))

rownames(mat) <- cuiStr
colnames(mat) <- as.character(sort(as.integer(durs)))[colFilter]
# creates a own color palette from red to green
my_palette <- c(colorRampPalette(c("grey90"),1)(n = 1),
                colorRampPalette(c("light green", "yellow", "orange", "red"),0.2)(n = 99) )

# (optional) defines the color breaks manually for a "skewed" color transition
col_breaks = c(seq(0,0.1,length=1),   # for grey
               seq(0.2,30,length=40),            # for green
               seq(30.1,150,length=30),          # for yellow
               seq(150.1,max(mat)+1,length=30))              # for red

# creates a 5 x 5 inch image
png("C:\\Users\\Jason\\Desktop\\cui_duration_heatmap.png",
    width = 5*400,        # 5 x 300 pixels
    height = 5*400,
    res = 400,            # 300 pixels per inch
    pointsize = 6)        # smaller font size


labels <- as.character(mat)
labels[mat==0] = ""
dim(labels) = dim(mat)
heatmap.2(mat,
          cellnote = labels,  # same data set for cell labels
          #main = "duration vs frequency for top N CUI", # heat map title
          notecol="black",      # change font color of cell labels to black
          density.info="histogram",  # turns off density plot inside color legend
          key.par=list(mar=c(3.5,1,3,1)),
          key.title = "frequency to color mapping",
          key.xlab = "",
          key.ylab = "",
          #labCol = colnames(mat),
          #labRow = seq(nrow(mat)),#rownames(mat),
          xlab = "Number of months",
          cexRow = 1.4,
          cexcol = 6, 
          srtRow = -23,
          trace="both",         # turns off trace lines inside the heat map
          tracecol="ghostwhite",
          margins =c(3.5,6),     # widens margins around plot, col and row
          col=my_palette,       # use on color palette defined earlier
          breaks=col_breaks,    # enable color transition at specified limits
          dendrogram="none",     # only draw a row dendrogram
          Colv="NA",             # turn off column clustering
          lmat=rbind(c(5, 4, 2), c(6, 1, 3)), 
          lhei=c(1.5, 9), 
          lwid=c(0.01, 10,1.9)
          )
#mtext("Number of months",side=1,line=3)

#nba_heatmap <- heatmap(mat, Rowv=NA, Colv=NA, col = cm.colors(256), scale="column")



# 
# library(d3heatmap)
# url <- "http://datasets.flowingdata.com/ppg2008.csv"
# nba_players <- read.csv(url, row.names = 1)
# d3heatmap(nba_players, scale = "column",dendrogram = "none",color = "Blues")
# 
# 
# install.packages("heatmaply")
# library(heatmaply)
# heatmaply(mtcars, k_col = 2, k_row = 3) %>% layout(margin = list(l = 130, b = 40))


dev.off()
