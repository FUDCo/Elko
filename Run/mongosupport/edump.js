function purgeFloatApprox(obj) {
    for (var p in obj) {
        if (obj[p] && typeof obj[p] == "object") {
            if (obj[p].floatApprox) {
                obj[p] = obj[p].floatApprox;
            }
            purgeFloatApprox(obj[p]);
        }
    }
}
function eexport(obj) {
    print("cat > " + obj.ref + " << END");
    delete obj._id;
    delete obj._qpos_;
    purgeFloatApprox(obj);
    print(tojson(obj));
    print("END");
}
db.odb.find({ref:{$exists:true}}).forEach(function(o){eexport(o);})
quit();
