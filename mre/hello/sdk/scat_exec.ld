/* Layout legado (ARM ADS): ER_RO em 0x8000, ER_RW/ER_ZI em RAM base 0.
   Usado por VMs antigas (BL6133 / W26) que nao carregam PIE. */
OUTPUT_FORMAT("elf32-littlearm", "elf32-bigarm", "elf32-littlearm")
OUTPUT_ARCH(arm)
ENTRY(gcc_entry)

SECTIONS
{
  . = 0x8000;
  ER_RO :
  {
    *(.text .text.*)
    *(.glue_7) *(.glue_7t) *(.v4_bx) *(.vfp11_veneer)
    *(.rodata .rodata.*)
    . = ALIGN(4);
    PROVIDE_HIDDEN (__init_array_start = .);
    KEEP (*(SORT(.init_array.*)))
    KEEP (*(.init_array))
    PROVIDE_HIDDEN (__init_array_end = .);
    KEEP (*(.fini_array))
    . = ALIGN(4);
    *(.ARM.extab* .gnu.linkonce.armextab.*)
    *(.ARM.exidx* .gnu.linkonce.armexidx.*)
    . = ALIGN(4);
  }

  . = 0x0;
  ER_RW :
  {
    *(.data .data.*)
    *(.got.plt) *(.igot.plt) *(.got) *(.igot)
    . = ALIGN(4);
  }

  ER_ZI :
  {
    *(.bss .bss.*)
    *(COMMON)
    . = ALIGN(4);
  }

  /DISCARD/ : { *(.comment) *(.note*) *(.gnu_debuglink) *(.gnu.lto_*) *(.stab*) *(.ARM.attributes) }
}
