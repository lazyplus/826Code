#!/bin/bash

N_STATE=4
ITER=5
HH=0.000002
GIVE_PRIOR=800
NODE_NUM=$(cat lbp.all.prior | wc -l)

./shuffle.py < lbp.all.prior > lbp.prior.all.tmp
head -n $GIVE_PRIOR lbp.prior.all.tmp > lbp.prior.all

./gen_one_vs_all_lbp.py $N_STATE

for ((i=0; i<$N_STATE; i++))
do
    echo $i
    cp lbp.prior.$i lbp.prior
    ./do_lbp.sh $NODE_NUM $ITER $HH
    mv run_tmp/lbp_output/part-00000 result.$i
done

./combine_lbp.py $N_STATE > one_vs_all_lbp.result
./verify.py lbp.all.prior one_vs_all_lbp.result
