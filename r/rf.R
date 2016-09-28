
#install.packages('randomForest')
library('randomForest')

data=read.csv("C:\\fsu\\class\\captilone\\data\\test_fraud_acc_p10\\ret_intm_fraud_acc.csv",header=TRUE)
#data=read.csv("/panfs/storage.local/home-3/zc15d/coc/fraud_acc/ret_intm_acc-fraud.csv",header=TRUE)

runs = 0
miscls.rf = NULL
miscls.logit = NULL
miscls.gam = NULL
library(mgcv)
#while(1)
#{
  # Split the data into training and test subsets
  trnInds = sample(nrow(data), 0.75*nrow(data))
  train = data[trnInds,]
  test = data[-trnInds,]
  
  # fit a rf model and make predictions
  rfmodel = randomForest(label ~ ., data=train,ntree=100,na.action=na.roughfix)
  rfpreds = predict(rfmodel, test,  type="response")
  conftab = table(test$label, rfpreds)
  miscls.rf = c(miscls.rf, 1-sum(diag(conftab))/sum(conftab))
  
  # fit a logistic model and make predictions
  logitmodel = glm(label ~ ., data=train, family=binomial)
  logitpreds = predict(logitmodel, newdata=test,  type="response")
  conftab = table(as.numeric(test$FRD_IND)-1, round(logitpreds))
  miscls.logit = c(miscls.logit, 1-sum(diag(conftab))/sum(conftab))
  
  gm = gam(label ~ ., data=train, family=binomial)
  gmpreds = predict(gm, newdata=test,  type="response")
  conftab = table(as.numeric(test$FRD_IND)-1, round(gmpreds))
  miscls.gam = c(miscls.gam, 1-sum(diag(conftab))/sum(conftab))
  
# if(runs > 50) break else runs = runs + 1
#}
cat('mean of rf:',  mean(miscls.rf), 'median:', median(miscls.rf),'\n')
cat('mean of lt:',  mean(miscls.logit), 'median:', median(miscls.logit),'\n')
cat('mean of gm:',  mean(miscls.gam), 'median:', median(miscls.gam),'\n')

# variable importance
importance(rfmodel, type=1) # through oob
importance(rfmodel, type=2) # measured by node impurities, proposed by Breiman (carrying over to any tree methods)

importance(rfmodel) 

varImpPlot(rfmodel)
