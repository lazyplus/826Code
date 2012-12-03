#!/bin/bash

N_STATE=3
ITER=5
HH=0.002
GIVE_PRIOR=87
NODE_NUM=$(cat lbp.all.prior | wc -l)
BIT_NUM=3

./shuffle.py < lbp.all.prior > lbp.prior.all.tmp
head -n $GIVE_PRIOR lbp.prior.all.tmp > lbp.prior.all

python gen_ecoc_lbp.py $N_STATE

for ((i=0; i<$BIT_NUM; i++))
do
    echo $i
    cp lbp.prior.$i lbp.prior
    ./do_lbp.sh $NODE_NUM $ITER $HH
    mv run_tmp/lbp_output/part-00000 result.$i
done

./combine_lbp.py $BIT_NUM > ecoc_lbp.result
python verify_ecoc.py lbp.all.prior ecoc_lbp.result $N_STATE
