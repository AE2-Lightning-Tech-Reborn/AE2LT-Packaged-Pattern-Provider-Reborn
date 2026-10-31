// FA JS only translates FA's own namespace. Supply the addon's core ritual for shared probes.
ServerEvents.recipes(event => {
    event.recipes.forbidden_arcanus.ritual(
        RitualResults.ofCreateItemResult(Item.of('ae2ltpp:fa_hephaestus_forge_packaged_core')),
        'ae2ltpp:basic_packaged_core'
    )
        .addInput('forbidden_arcanus:deorum_ingot', 2)
        .addInput('forbidden_arcanus:dark_matter', 1)
        .essences(20, 1, 100, 10)
        .tier(1)
        .magicCircles('forbidden_arcanus:create_item')
        .id('ae2ltpp:probe_core');
});
