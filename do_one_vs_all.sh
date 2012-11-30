#!/bin/bash

N_STATE=3
ITER=100
GIVE_PRIOR=87
NODE_NUM=$(cat bp.all.prior | wc -l)

./shuffle.py < bp.all.prior > bp.prior.all.tmp
head -n $GIVE_PRIOR bp.prior.all.tmp > bp.prior.all

./gen_one_vs_all.py $N_STATE

for ((i=0; i<$N_STATE; i++))
do
    echo $i
    cp bp.prior.$i bp.prior
    cp bp.ep.$i bp.ep
    ./do_bp.sh $ITER 2 $NODE_NUM
    mv run_tmp/bp_output/part-00000 result.$i
done

./combine.py $N_STATE > one_vs_all.result
./verify.py bp.all.prior one_vs_all.result
